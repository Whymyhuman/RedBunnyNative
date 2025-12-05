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
import random

# --- SUMBER V2RAY (VLESS/VMess/Trojan) ---
SOURCES = [
    "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/vless",
    "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/vmess",
    "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/trojan",
    "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/server.txt",
    "https://raw.githubusercontent.com/soroushmirza5/telegram-configs-collector/main/protocols/vless",
    "https://raw.githubusercontent.com/soroushmirza5/telegram-configs-collector/main/protocols/vmess",
    "https://raw.githubusercontent.com/mrzero0nol/My-v2ray/main/proxyList.txt",
    "https://raw.githubusercontent.com/freefq/free/master/vless.txt",
    "https://raw.githubusercontent.com/rostergamer/v2ray/master/vless"
]

# Target Check: Speedtest Config (Ringan)
TARGET_URL = "https://www.speedtest.net/api/js/servers?engine=js"
XRAY_BIN = "backend/xray_bin/xray"
LOCAL_PORT_START = 10000

def try_decode(text):
    text = text.strip()
    if not text: return ""
    if "://" in text: return text
    try: return base64.b64decode(text).decode('utf-8', errors='ignore')
    except:
        try: return base64.b64decode(text + "==").decode('utf-8', errors='ignore')
        except: return text

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=20) as response:
            if response.status == 200:
                text = await response.text()
                if not " " in text[:50] and len(text) > 50: return try_decode(text)
                return text
    except: pass
    return ""

# --- PARSERS (RELAXED) ---
def parse_vless_trojan(uri):
    try:
        # vless://uuid@ip:port?params#name
        protocol = uri.split("://")[0]
        main = uri.split("://")[1]
        if "@" not in main: return None
        
        uuid, rest = main.split("@", 1)
        if "#" in rest: addr, _ = rest.split("#", 1)
        else: addr = rest
            
        if "?" in addr: addr_port, params_str = addr.split("?", 1)
        else: return None 
            
        # Handle IPv6 brackets [::1]:443
        if "]:" in addr_port:
            ip = addr_port.split("]:")[0].replace("[", "")
            port = addr_port.split("]:")[1]
        elif ":" in addr_port:
            ip, port = addr_port.split(":")
        else: return None
        
        port = int(port)
        params = dict(urllib.parse.parse_qsl(params_str))
        
        # HAPUS FILTER PORT 443 agar lebih banyak hasil
        # Tapi tetap wajib TLS agar bisa SNI trick
        # if params.get("security") != "tls": return None 

        return {
            "protocol": protocol,
            "uuid": uuid,
            "ip": ip,
            "port": port,
            "type": params.get("type", "tcp"),
            "path": params.get("path", "/"),
            "host": params.get("host", ""),
            "sni": params.get("sni", ""),
            "security": params.get("security", "none")
        }
    except: return None

def parse_vmess(uri):
    try:
        if not uri.startswith("vmess://"): return None
        b64 = uri.replace("vmess://", "")
        json_str = base64.b64decode(b64 + "===").decode('utf-8', errors='ignore')
        data = json.loads(json_str)
        
        return {
            "protocol": "vmess",
            "uuid": data.get("id"),
            "ip": data.get("add"),
            "port": int(data.get("port", 0)),
            "type": data.get("net", "tcp"),
            "path": data.get("path", "/"),
            "host": data.get("host", ""),
            "sni": data.get("sni") or data.get("host", ""),
            "security": data.get("tls", ""),
            "aid": data.get("aid", 0)
        }
    except: return None

def generate_xray_config(data, local_port):
    outbound = {
        "protocol": data["protocol"],
        "settings": {},
        "streamSettings": {
            "network": data["type"],
            "security": data["security"],
            "tlsSettings": {"serverName": data["sni"] or data["host"], "allowInsecure": True} if data["security"] == "tls" else None,
            "wsSettings": {"path": data["path"], "headers": {"Host": data["host"]}} if data["type"] == "ws" else None,
            "grpcSettings": {"serviceName": data["path"]} if data["type"] == "grpc" else None
        }
    }

    if data["protocol"] == "vmess":
        outbound["settings"]["vnext"] = [{
            "address": data["ip"], "port": data["port"],
            "users": [{"id": data["uuid"], "alterId": int(data.get("aid", 0)), "security": "auto"}]
        }]
    else:
        outbound["settings"]["vnext"] = [{
            "address": data["ip"], "port": data["port"],
            "users": [{"id": data["uuid"], "encryption": "none"}] if data["protocol"] == "vless" else [{"password": data["uuid"]}]
        }]
    
    return json.dumps({
        "log": {"loglevel": "none"},
        "inbounds": [{"port": local_port, "listen": "127.0.0.1", "protocol": "socks", "settings": {"udp": True}}],
        "outbounds": [outbound]
    })

async def check_proxy(proxy_str, idx):
    data = None
    if proxy_str.startswith("vmess://"): data = parse_vmess(proxy_str)
    else: data = parse_vless_trojan(proxy_str)
    
    if not data: return None
    
    local_port = LOCAL_PORT_START + (idx % 50)
    cfg_file = f"config_{local_port}.json"
    
    with open(cfg_file, "w") as f: f.write(generate_xray_config(data, local_port))
        
    proc = None
    try:
        proc = subprocess.Popen([XRAY_BIN, "-c", cfg_file], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        await asyncio.sleep(1.5)
        
        async with aiohttp.ClientSession() as session:
            # Timeout 15 detik
            async with session.get(TARGET_URL, proxy=f"socks5://127.0.0.1:{local_port}", timeout=15, ssl=False) as response:
                if response.status == 200:
                    return proxy_str
    except: pass
    finally:
        if proc: proc.kill()
        if os.path.exists(cfg_file): os.remove(cfg_file)
    return None

async def main():
    print("🚀 Starting Relaxed Aggregator...")
    candidates = set()
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        for content in results:
            if not content: continue
            if not " " in content[:50] and len(content) > 100: content = try_decode(content)
            for line in content.splitlines():
                line = line.strip()
                if line.startswith(("vless://", "trojan://", "vmess://")):
                    candidates.add(line)

    print(f"📥 Collected {len(candidates)} candidates. Checking...")
    
    check_list = list(candidates)
    random.shuffle(check_list)
    check_list = check_list[:2000] # Sample 2000
    
    sem = asyncio.Semaphore(20)
    tasks = []
    for i, p in enumerate(check_list):
        async def wrapped(proxy, idx):
            async with sem: return await check_proxy(proxy, idx)
        tasks.append(wrapped(p, i))
        
    results = await asyncio.gather(*tasks)
    active = [r for r in results if r]
    
    print(f"✅ VERIFIED WORKING: {len(active)}")
    with open("active_proxies.txt", "w") as f: f.write("\n".join(active))

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
