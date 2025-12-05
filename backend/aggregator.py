import requests
import re
import asyncio
import aiohttp
import time
import base64
import json
import subprocess
import os
import socket
import urllib.parse

# --- KONFIGURASI ---
SOURCES = [
    "https://raw.githubusercontent.com/mrzero0nol/My-v2ray/main/proxyList.txt",
    "https://raw.githubusercontent.com/freefq/free/master/vless.txt",
    "https://raw.githubusercontent.com/rostergamer/v2ray/master/vless",
    "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub",
    "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list.txt",
    "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt",
    "https://raw.githubusercontent.com/mfuu/v2ray/master/vless",
    "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
    "https://raw.githubusercontent.com/Barimehdi/sub_v2ray/refs/heads/main/vless.txt",
    "https://raw.githubusercontent.com/aiboboxx/v2rayfree/main/v2ray"
]

# Kita fokus ke VLESS saja dulu karena ini yang paling tricky
TARGET_URL = "https://www.speedtest.net" # HTTPS check!
XRAY_BIN = "backend/xray_bin/xray"
LOCAL_PORT_START = 10000

def decode_base64(s):
    try: return base64.b64decode(s).decode('utf-8', errors='ignore')
    except: return s

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=15) as response:
            if response.status == 200:
                text = await response.text()
                if not " " in text[:50] and len(text) > 20:
                    return decode_base64(text)
                return text
    except: pass
    return ""

def parse_vless(uri):
    try:
        # vless://uuid@ip:port?params#name
        if not uri.startswith("vless://"): return None
        
        main_part = uri.replace("vless://", "")
        if "@" not in main_part: return None
        
        uuid, rest = main_part.split("@", 1)
        
        if "#" in rest:
            addr_part, hash_part = rest.split("#", 1)
        else:
            addr_part = rest
            
        if "?" in addr_part:
            addr_port, params_str = addr_part.split("?", 1)
        else:
            addr_port = addr_part
            params_str = ""
            
        ip, port = addr_port.split(":")
        port = int(port)
        
        params = dict(urllib.parse.parse_qsl(params_str))
        
        return {
            "uuid": uuid,
            "ip": ip,
            "port": port,
            "type": params.get("type", "tcp"),
            "path": params.get("path", "/"),
            "host": params.get("host", ""),
            "sni": params.get("sni", ""),
            "security": params.get("security", "none")
        }
    except:
        return None

def generate_xray_config(vless_data, local_port):
    # Template config client Xray minimalis
    outbound = {
        "protocol": "vless",
        "settings": {
            "vnext": [{
                "address": vless_data["ip"],
                "port": vless_data["port"],
                "users": [{"id": vless_data["uuid"], "encryption": "none"}]
            }]
        },
        "streamSettings": {
            "network": vless_data["type"],
            "security": vless_data["security"],
        }
    }
    
    # Tambahkan detail transport
    if vless_data["type"] == "ws":
        outbound["streamSettings"]["wsSettings"] = {
            "path": vless_data["path"],
            "headers": {"Host": vless_data["host"]} if vless_data["host"] else {}
        }
    
    if vless_data["security"] == "tls":
        outbound["streamSettings"]["tlsSettings"] = {
            "serverName": vless_data["sni"] or vless_data["host"]
        }

    config = {
        "log": {"loglevel": "error"},
        "inbounds": [{
            "port": local_port,
            "listen": "127.0.0.1",
            "protocol": "socks",
            "settings": {"udp": True}
        }],
        "outbounds": [outbound]
    }
    return json.dumps(config)

async def check_proxy_with_xray(proxy_uri, port_offset):
    vless_data = parse_vless(proxy_uri)
    if not vless_data: return None
    
    local_port = LOCAL_PORT_START + port_offset
    config_str = generate_xray_config(vless_data, local_port)
    
    # Tulis config sementara
    config_file = f"config_{local_port}.json"
    with open(config_file, "w") as f:
        f.write(config_str)
        
    process = None
    try:
        # Jalankan Xray
        process = subprocess.Popen([XRAY_BIN, "-c", config_file], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        
        # Tunggu Xray siap
        await asyncio.sleep(1)
        
        # Cek koneksi via proxy lokal
        start = time.time()
        proxy_url = f"socks5://127.0.0.1:{local_port}"
        
        async with aiohttp.ClientSession() as session:
            # Timeout ketat 10 detik untuk loading halaman speedtest
            async with session.get(TARGET_URL, proxy=proxy_url, timeout=10, ssl=False) as response:
                # Jika bisa load halaman (status 200), berarti VLESS valid!
                if response.status == 200:
                    return proxy_uri
                    
    except Exception as e:
        # print(f"Failed: {e}")
        pass
    finally:
        if process: process.kill()
        if os.path.exists(config_file): os.remove(config_file)
        
    return None

async def main():
    print(f"🚀 Starting Real Xray Checker...")
    vless_proxies = set()
    
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        
        for content in results:
            if not content: continue
            for line in content.splitlines():
                if line.strip().startswith("vless://"):
                    vless_proxies.add(line.strip())

    print(f"📥 Collected: {len(vless_proxies)} potential VLESS candidates.")
    
    # Batasi concurrency karena Xray makan CPU
    sem = asyncio.Semaphore(20) 
    
    tasks = []
    for i, p in enumerate(list(vless_proxies)[:500]): # Sample 500 agar tidak timeout di Action
        async def wrapped_check(proxy, idx):
            async with sem:
                return await check_proxy_with_xray(proxy, idx % 50) # Reuse ports
        tasks.append(wrapped_check(p, i))
        
    results = await asyncio.gather(*tasks)
    active_proxies = [r for r in results if r]
    
    print(f"✅ VERIFIED Active VLESS (Speedtest Access): {len(active_proxies)}")

    with open("active_proxies.txt", "w") as f:
        f.write("\n".join(active_proxies))
        
    # Backup all
    with open("all_proxies.txt", "w") as f:
        f.write("\n".join(vless_proxies))

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())