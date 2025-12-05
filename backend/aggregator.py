import requests
import re
import asyncio
import aiohttp
import time
import base64
import json
import urllib.parse
import random

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

def try_decode(text):
    text = text.strip()
    if not text: return ""
    if "://" in text: return text
    try: return base64.b64decode(text).decode('utf-8', errors='ignore')
    except:
        try: return base64.b64decode(text + "===").decode('utf-8', errors='ignore')
        except: return text

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=15) as response:
            if response.status == 200:
                text = await response.text()
                if len(text) > 50 and " " not in text[:50]: return try_decode(text)
                return text
    except: pass
    return ""

def parse_vless_trojan(uri):
    try:
        if "://" not in uri: return None
        main = uri.split("://")[1]
        if "@" not in main: return None
        
        if "#" in main: addr = main.split("#")[0].split("@")[1]
        else: addr = main.split("@")[1]
            
        if "?" in addr: addr_port = addr.split("?")[0]
        else: addr_port = addr
            
        if "]:" in addr_port:
            ip = addr_port.split("]:")[0].replace("[", "")
            port = addr_port.split("]:")[1]
        elif ":" in addr_port:
            ip, port = addr_port.split(":")
        else: return None
        
        return {"ip": ip, "port": int(port), "uri": uri}
    except: return None

def parse_vmess(uri):
    try:
        b64 = uri.replace("vmess://", "")
        json_str = base64.b64decode(b64 + "===").decode('utf-8', errors='ignore')
        data = json.loads(json_str)
        return {"ip": data.get("add"), "port": int(data.get("port")), "uri": uri}
    except: return None

async def check_tcp(ip, port):
    try:
        fut = asyncio.open_connection(ip, port)
        reader, writer = await asyncio.wait_for(fut, timeout=3)
        writer.close()
        await writer.wait_closed()
        return True
    except:
        return False

async def main():
    print("🚀 Starting TCP Aggregator (443 Only)...")
    candidates = []
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        for content in results:
            if not content: continue
            for line in content.splitlines():
                line = line.strip()
                data = None
                if line.startswith("vmess://"): data = parse_vmess(line)
                elif line.startswith(("vless://", "trojan://")): data = parse_vless_trojan(line)
                
                # STRICT FILTER: Port 443 Only
                if data and data["port"] == 443:
                    candidates.append(data)

    print(f"📥 Collected {len(candidates)} candidates on Port 443. Checking TCP...")
    
    # Randomize & Limit
    random.shuffle(candidates)
    candidates = candidates[:3000]
    
    sem = asyncio.Semaphore(100) # Fast TCP check
    tasks = []
    
    async def wrapped_check(c):
        async with sem: 
            if await check_tcp(c["ip"], c["port"]):
                return c["uri"]
            return None

    tasks = [wrapped_check(c) for c in candidates]
    results = await asyncio.gather(*tasks)
    active = [r for r in results if r]
    
    print(f"✅ VERIFIED ACTIVE (TCP 443): {len(active)}")
    with open("active_proxies.txt", "w") as f: f.write("\n".join(active))

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
