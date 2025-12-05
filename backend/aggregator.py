import requests
import re
import asyncio
import aiohttp
import time
import base64
import json
import subprocess
import os
import urllib.parse

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

TARGET_URL = "http://www.speedtest.net"
XRAY_BIN = "backend/xray_bin/xray"
LOCAL_PORT_START = 10000

def try_decode(text):
    """Coba decode Base64 dengan berbagai padding"""
    text = text.strip()
    if not text: return ""
    
    # Jika sudah terlihat seperti vless://, kembalikan langsung
    if "vless://" in text or "trojan://" in text:
        return text

    # Coba decode
    try:
        return base64.b64decode(text).decode('utf-8', errors='ignore')
    except:
        try:
            # Tambah padding =
            return base64.b64decode(text + "==").decode('utf-8', errors='ignore')
        except:
            return text

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=20) as response:
            if response.status == 200:
                text = await response.text()
                return try_decode(text)
    except:
        pass
    return ""

def parse_vless(uri):
    try:
        if not uri.startswith("vless://"): return None
        main_part = uri.replace("vless://", "")
        
        # Handle cases where there is no @ (rare but possible in some formats)
        if "@" in main_part:
            uuid, rest = main_part.split("@", 1)
        else:
            return None

        if "?" in rest:
            addr_part, params_part = rest.split("?", 1)
            if "#" in params_part:
                params_str = params_part.split("#")[0]
            else:
                params_str = params_part
        else:
            return None
            
        if ":" in addr_part:
            ip, port = addr_part.split(":")
            port = int(port)
        else:
            return None
            
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
    
    if vless_data["type"] == "ws":
        outbound["streamSettings"]["wsSettings"] = {
            "path": vless_data["path"],
            "headers": {"Host": vless_data["host"]} if vless_data["host"] else {}
        }
    
    if vless_data["security"] == "tls":
        outbound["streamSettings"]["tlsSettings"] = {
            "serverName": vless_data["sni"] or vless_data["host"],
            "allowInsecure": True # Allow invalid certs for wider compatibility
        }

    config = {
        "log": {"loglevel": "none"},
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
    config_file = f"config_{local_port}.json"
    
    with open(config_file, "w") as f:
        f.write(config_str)
        
    process = None
    try:
        process = subprocess.Popen([XRAY_BIN, "-c", config_file], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        await asyncio.sleep(1.5) # Give Xray time to start
        
        proxy_url = f"socks5://127.0.0.1:{local_port}"
        async with aiohttp.ClientSession() as session:
            # Cek akses ke Speedtest config (ringan dan cepat)
            async with session.get("https://www.speedtest.net/api/js/servers?engine=js", proxy=proxy_url, timeout=8, ssl=False) as response:
                if response.status == 200:
                    return proxy_uri
    except:
        pass
    finally:
        if process: process.kill()
        if os.path.exists(config_file): os.remove(config_file)
        
    return None

async def main():
    print(f"🚀 Starting Enhanced Aggregator...")
    vless_proxies = set()
    
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        
        for content in results:
            if not content: continue
            # Decode again if it looks like base64 inside base64
            if not " " in content[:50] and len(content) > 100:
                content = try_decode(content)
                
            for line in content.splitlines():
                line = line.strip()
                if line.startswith("vless://"):
                    vless_proxies.add(line)

    print(f"📥 Collected: {len(vless_proxies)} potential VLESS candidates.")
    
    sem = asyncio.Semaphore(15) # Reduce concurrency slightly for stability
    tasks = []
    # Check ALL candidates (no sampling) but with semaphore
    for i, p in enumerate(list(vless_proxies)):
        async def wrapped_check(proxy, idx):
            async with sem:
                return await check_proxy_with_xray(proxy, idx % 50)
        tasks.append(wrapped_check(p, i))
        
    results = await asyncio.gather(*tasks)
    active_proxies = [r for r in results if r]
    
    print(f"✅ VERIFIED Active VLESS (Speedtest Access): {len(active_proxies)}")

    with open("active_proxies.txt", "w") as f:
        f.write("\n".join(active_proxies))

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
