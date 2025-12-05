import requests
import re
import asyncio
import aiohttp
import time
import base64
import json
import urllib.parse
import random

# --- SUMBER V2RAY (Updated & Expanded) ---
SOURCES = [
    # Iranian & Global Collectors (High Frequency)
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
    "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub",
    "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt",
    
    # Classic Sources
    "https://raw.githubusercontent.com/mrzero0nol/My-v2ray/main/proxyList.txt",
    "https://raw.githubusercontent.com/freefq/free/master/vless.txt",
    "https://raw.githubusercontent.com/freefq/free/master/vmess.txt",
    "https://raw.githubusercontent.com/rostergamer/v2ray/master/vless",
    "https://raw.githubusercontent.com/rostergamer/v2ray/master/vmess",
    "https://raw.githubusercontent.com/mfuu/v2ray/master/vless",
    "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
    "https://raw.githubusercontent.com/Barimehdi/sub_v2ray/refs/heads/main/vless.txt",
    "https://raw.githubusercontent.com/aiboboxx/v2rayfree/main/v2ray"
]

GEO_API_BATCH = "http://ip-api.com/batch"

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

def parse_proxy(uri):
    try:
        if uri.startswith("vmess://"):
            b64 = uri.replace("vmess://", "")
            json_str = base64.b64decode(b64 + "===").decode('utf-8', errors='ignore')
            data = json.loads(json_str)
            return {
                "protocol": "vmess",
                "ip": data.get("add"),
                "port": int(data.get("port")),
                "data": data,
                "uri": uri
            }
        elif uri.startswith(("vless://", "trojan://")):
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
            
            return {"protocol": "vless", "ip": ip, "port": int(port), "uri": uri}
    except: return None

def rename_proxy(proxy_info, geo_data):
    ip = proxy_info["ip"]
    info = geo_data.get(ip, {})
    country = info.get("countryCode", "UN")
    isp = info.get("isp", "Unknown")
    
    # Clean ISP name (remove AS number, short form)
    isp = re.sub(r'AS\d+', '', isp).strip()
    if len(isp) > 20: isp = isp[:20] + ".."
    
    new_name = f"{country} {isp}"
    # URL Encode name
    encoded_name = urllib.parse.quote(new_name)
    
    uri = proxy_info["uri"]
    if proxy_info["protocol"] == "vmess":
        data = proxy_info["data"]
        data["ps"] = new_name
        new_b64 = base64.b64encode(json.dumps(data).encode()).decode()
        return f"vmess://{new_b64}"
    else:
        # VLESS/Trojan: Replace or add hash
        if "#" in uri:
            return uri.split("#")[0] + f"#{encoded_name}"
        else:
            return uri + f"#{encoded_name}"

async def check_tcp(ip, port):
    try:
        fut = asyncio.open_connection(ip, port)
        reader, writer = await asyncio.wait_for(fut, timeout=3)
        writer.close()
        await writer.wait_closed()
        return True
    except: return False

async def resolve_geoip_batch(ips, session):
    results = {}
    # Batch size 100 (limit ip-api)
    chunks = [list(ips)[i:i + 100] for i in range(0, len(ips), 100)]
    
    for chunk in chunks:
        try:
            async with session.post(GEO_API_BATCH, json=chunk, timeout=10) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    for item in data:
                        if "query" in item:
                            results[item["query"]] = item
        except: pass
    return results

async def main():
    print("🚀 Starting Aggregator + GeoIP Renamer...")
    
    # 1. Scrape
    candidates = []
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        for content in results:
            if not content: continue
            for line in content.splitlines():
                line = line.strip()
                p = parse_proxy(line)
                if p and p["port"] == 443: # Filter 443
                    candidates.append(p)

    print(f"📥 Collected {len(candidates)} candidates (Port 443).")
    
    # 2. Check TCP
    random.shuffle(candidates)
    check_list = candidates[:3000]
    
    sem = asyncio.Semaphore(100)
    active_list = []
    
    async def wrapped_check(c):
        async with sem:
            if await check_tcp(c["ip"], c["port"]):
                return c
            return None

    tasks = [wrapped_check(c) for c in check_list]
    results = await asyncio.gather(*tasks)
    active_list = [r for r in results if r]
    
    print(f"✅ Active TCP: {len(active_list)}")
    
    # 3. GeoIP & Rename
    if active_list:
        unique_ips = set(p["ip"] for p in active_list)
        print(f"🌍 Resolving GeoIP for {len(unique_ips)} IPs...")
        
        async with aiohttp.ClientSession() as session:
            geo_data = await resolve_geoip_batch(unique_ips, session)
            
        final_proxies = []
        for p in active_list:
            final_proxies.append(rename_proxy(p, geo_data))
            
        print(f"💾 Saving {len(final_proxies)} named proxies...")
        with open("active_proxies.txt", "w") as f: f.write("\n".join(final_proxies))
    else:
        print("⚠️ No active proxies found.")
        with open("active_proxies.txt", "w") as f: f.write("")

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())