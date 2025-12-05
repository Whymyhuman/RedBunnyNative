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
    # --- KOLEKTOR POPULER (Update Tiap Jam) ---
    "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/vless",
    "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/vmess",
    "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/trojan",
    "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/server.txt",
    "https://raw.githubusercontent.com/soroushmirza5/telegram-configs-collector/main/protocols/vless",
    "https://raw.githubusercontent.com/soroushmirza5/telegram-configs-collector/main/protocols/vmess",
    "https://raw.githubusercontent.com/soroushmirza5/telegram-configs-collector/main/protocols/trojan",
    "https://raw.githubusercontent.com/AzadNetCH/Clash/main/V2Ray/V2Ray-64.txt",
    "https://raw.githubusercontent.com/ts-sf/fly/main/v2",
    "https://raw.githubusercontent.com/mkb2023/One-Click-Proxy/main/v2ray.txt",
    
    # --- SUMBER LAMA (Tapi Masih Bagus) ---
    "https://raw.githubusercontent.com/mrzero0nol/My-v2ray/main/proxyList.txt",
    "https://raw.githubusercontent.com/Epodonios/v2ray-configs/master/Vless",
    "https://raw.githubusercontent.com/Epodonios/v2ray-configs/master/Vmess",
    "https://raw.githubusercontent.com/Epodonios/v2ray-configs/master/Trojan",
    "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub/Vless",
    "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub/Vmess",
    "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub/Trojan",
    "https://raw.githubusercontent.com/sevcator/5ubscrpt10n/main/vless.txt",
    "https://raw.githubusercontent.com/sevcator/5ubscrpt10n/main/vmess.txt",
    "https://raw.githubusercontent.com/sevcator/5ubscrpt10n/main/trojan.txt",
    "https://raw.githubusercontent.com/ebrasha/free-v2ray-public-list/main/vless",
    "https://raw.githubusercontent.com/ebrasha/free-v2ray-public-list/main/vmess",
    "https://raw.githubusercontent.com/ebrasha/free-v2ray-public-list/main/trojan",
    "https://raw.githubusercontent.com/freefq/free/master/vless.txt",
    "https://raw.githubusercontent.com/freefq/free/master/vmess.txt",
    "https://raw.githubusercontent.com/rostergamer/v2ray/master/vless",
    "https://raw.githubusercontent.com/rostergamer/v2ray/master/vmess",
    "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub",
    "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list.txt",
    "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt",
    "https://raw.githubusercontent.com/mfuu/v2ray/master/vless",
    "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
    "https://raw.githubusercontent.com/Barimehdi/sub_v2ray/refs/heads/main/vless.txt",
    "https://raw.githubusercontent.com/aiboboxx/v2rayfree/main/v2ray"
]

# Target Check: Speedtest API
TARGET_URL = "https://www.speedtest.net/api/js/servers?engine=js"
XRAY_BIN = "backend/xray_bin/xray"
LOCAL_PORT_START = 10000

def try_decode(text):
    text = text.strip()
    if not text: return ""
    if "://" in text: return text
    try: return base64.b64decode(text).decode('utf-8', errors='ignore')
    except:
        try: return base64.b64decode(text + "=").decode('utf-8', errors='ignore')
        except:
            try: return base64.b64decode(text + "==").decode('utf-8', errors='ignore')
            except: return text

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=15) as response:
            if response.status == 200:
                text = await response.text()
                if not " " in text[:50] and len(text) > 50:
                    return try_decode(text)
                return text
    except: pass
    return ""

# --- PARSERS ---
def parse_vless_trojan(uri):
    try:
        protocol = uri.split("://")[0]
        main = uri.split("://")[1]
        if "@" not in main: return None
        uuid, rest = main.split("@", 1)
        
        if "#" in rest: addr, _ = rest.split("#", 1)
        else: addr = rest
            
        if "?" in addr: addr_port, params_str = addr.split("?", 1)
        else: return None
            
        if ":" not in addr_port: return None
        ip, port = addr_port.split(":")
        port = int(port)
        
        if port != 443: return None # Filter Port 443 Only

        params = dict(urllib.parse.parse_qsl(params_str))
        if params.get("security") != "tls": return None # Filter TLS Only

        return {
            "protocol": protocol,
            "uuid": uuid,
            "ip": ip,
            "port": port,
            "type": params.get("type", "tcp"),
            "path": params.get("path", "/"),
            "host": params.get("host", ""),
            "sni": params.get("sni", ""),
            "security": "tls"
        }
    except: return None

def parse_vmess(uri):
    try:
        if not uri.startswith("vmess://"): return None
        b64 = uri.replace("vmess://", "")
        json_str = base64.b64decode(b64 + "===").decode('utf-8', errors='ignore')
        data = json.loads(json_str)
        
        port = int(data.get("port", 0))
        if port != 443: return None
        if data.get("tls") != "tls": return None

        return {
            "protocol": "vmess",
            "uuid": data.get("id"),
            "ip": data.get("add"),
            "port": port,
            "type": data.get("net", "tcp"),
            "path": data.get("path", "/"),
            "host": data.get("host", ""),
            "sni": data.get("sni") or data.get("host", ""),
            "security": "tls",
            "aid": data.get("aid", 0)
        }
    except: return None

def generate_xray_config(data, local_port):
    outbound = {
        "protocol": data["protocol"],
        "settings": {},
        "streamSettings": {
            "network": data["type"],
            "security": "tls",
            "tlsSettings": {
                "serverName": data["sni"] or data["host"],
                "allowInsecure": True
            }
        }
    }

    if data["protocol"] == "vmess":
        outbound["settings"]["vnext"] = [{
            "address": data["ip"],
            "port": data["port"],
            "users": [{"id": data["uuid"], "alterId": int(data.get("aid", 0)), "security": "auto"}]
        }]
    else:
        outbound["settings"]["vnext"] = [{
            "address": data["ip"],
            "port": data["port"],
            "users": [{"id": data["uuid"], "encryption": "none"}] if data["protocol"] == "vless" else [{"password": data["uuid"]}]
        }]

    if data["type"] == "ws":
        outbound["streamSettings"]["wsSettings"] = {
            "path": data["path"],
            "headers": {"Host": data["host"]}
        }
    
    config = {
        "log": {"loglevel": "none"},
        "inbounds": [{"port": local_port, "listen": "127.0.0.1", "protocol": "socks", "settings": {"udp": True}}],
        "outbounds": [outbound]
    }
    return json.dumps(config)

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
        
        proxy_url = f"socks5://127.0.0.1:{local_port}"
        async with aiohttp.ClientSession() as session:
            async with session.get(TARGET_URL, proxy=proxy_url, timeout=10, ssl=False) as response:
                if response.status == 200:
                    return proxy_str
    except: pass
    finally:
        if proc: proc.kill()
        if os.path.exists(cfg_file): os.remove(cfg_file)
    return None

async def main():
    print("🚀 Starting Aggregator with NEW SOURCES...")
    candidates = set()
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        for content in results:
            if not content: continue
            if not " " in content[:50] and len(content) > 100: content = try_decode(content)
            for line in content.splitlines():
                line = line.strip()
                if line.startswith("vless://") or line.startswith("trojan://") or line.startswith("vmess://"):
                    candidates.add(line)

    print(f"📥 Collected {len(candidates)} candidates. Filtering Port 443/TLS...")
    
    check_list = list(candidates)
    random.shuffle(check_list)
    check_list = check_list[:2000] # Naikkan limit sample jadi 2000
    
    sem = asyncio.Semaphore(25)
    tasks = []
    for i, p in enumerate(check_list):
        async def wrapped(proxy, idx):
            async with sem: return await check_proxy(proxy, idx)
        tasks.append(wrapped(p, i))
        
    results = await asyncio.gather(*tasks)
    active = [r for r in results if r]
    
    print(f"✅ VERIFIED 443/TLS PROXIES: {len(active)}")
    with open("active_proxies.txt", "w") as f: f.write("\n".join(active))

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())