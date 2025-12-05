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

# --- SUMBER RAW IP (HTTP/SOCKS) ---
SOURCES = [
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
    "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
    "https://raw.githubusercontent.com/zloi-user/hideip.me/main/http.txt",
    "https://raw.githubusercontent.com/saisuiu/Lionkings-Http-Proxys-Proxies/main/free.txt",
    "https://raw.githubusercontent.com/caliphdev/Proxy-List/master/http.txt",
    "https://raw.githubusercontent.com/sunny9577/proxy-scraper/master/proxies.txt",
    "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
    "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt",
    "https://raw.githubusercontent.com/shiftytr/proxy-list/master/proxy.txt",
    "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt",
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt"
]

# Regex IP:Port Simple
REGEX_IP_PORT = re.compile(r'(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})[:\s,\t]+(\d{2,5})')

# Target Check: Speedtest API (Ringan & Cepat)
TARGET_URL = "https://www.speedtest.net/api/js/servers?engine=js"
XRAY_BIN = "backend/xray_bin/xray"
LOCAL_PORT_START = 10000

# --- TEMPLATE VLESS DAUSLEARN ---
def create_tunnel_link(ip, port):
    # vless://uuid@address:port?query#hash
    base = "vless://96900e2c-fd86-4207-abc1-91ac57cf931d@dauslearn.dpdns.org:443"
    params = (
        "?type=ws&"
        "encryption=none&"
        "flow=&"
        "host=media-sin6-3.cdn.whatsapp.net.dauslearn.dpdns.org&"
        f"path=%2F{ip}-{port}&"
        "security=tls&"
        "sni=media-sin6-3.cdn.whatsapp.net.dauslearn.dpdns.org"
    )
    hashtag = f"#Tunnel-{ip}"
    return f"{base}/{params}{hashtag}"

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=15) as response:
            if response.status == 200:
                return await response.text()
    except: pass
    return ""

# Parser VLESS untuk Xray Config Generator (Standard)
def parse_vless(uri):
    try:
        if not uri.startswith("vless://"):
            return None
        main_part = uri.replace("vless://", "")
        if "@" not in main_part:
            return None
        uuid, rest = main_part.split("@", 1)
        
        if "#" in rest:
            addr_part, _ = rest.split("#", 1)
        else:
            addr_part = rest
            
        if "?" in addr_part:
            addr_port, params_str = addr_part.split("?", 1)
        else:
            return None
            
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
    except: return None

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
            "allowInsecure": True
        }

    config = {
        "log": {"loglevel": "none"},
        "inbounds": [{"port": local_port, "listen": "127.0.0.1", "protocol": "socks", "settings": {"udp": True}}],
        "outbounds": [outbound]
    }
    return json.dumps(config)

async def check_proxy_with_xray(proxy_uri, port_offset):
    vless_data = parse_vless(proxy_uri)
    if not vless_data:
        return None
    
    local_port = LOCAL_PORT_START + port_offset
    config_file = f"config_{local_port}.json"
    
    with open(config_file, "w") as f:
        f.write(generate_xray_config(vless_data, local_port))
        
    process = None
    try:
        process = subprocess.Popen([XRAY_BIN, "-c", config_file], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        await asyncio.sleep(1.5) # Wait for Xray to boot
        
        proxy_url = f"socks5://127.0.0.1:{local_port}"
        async with aiohttp.ClientSession() as session:
            # Cek Speedtest Config
            async with session.get(TARGET_URL, proxy=proxy_url, timeout=8, ssl=False) as response:
                if response.status == 200:
                    # Double check: Pastikan response valid JSON dari speedtest
                    text = await response.text()
                    if "servers" in text or "id" in text:
                        return proxy_uri
    except: 
        pass
    finally:
        if process:
            process.kill()
        if os.path.exists(config_file):
            os.remove(config_file)
        
    return None

async def main():
    print(f"🚀 Starting Tunnel Aggregator (Target: dauslearn.dpdns.org)...")
    
    raw_ips = set()
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        
        for content in results:
            if not content:
                continue
            matches = REGEX_IP_PORT.findall(content)
            for ip, port in matches:
                # Filter local ips
                if not ip.startswith("127.") and not ip.startswith("192.168."):
                    raw_ips.add((ip, port))

    print(f"📥 Found {len(raw_ips)} RAW proxies. Wrapping into VLESS tunnels...")
    
    # Convert all RAW IPs to VLESS Tunnel Links
    candidates = []
    for ip, port in list(raw_ips): 
        link = create_tunnel_link(ip, port)
        candidates.append(link)
        
    print(f"🔍 Checking {len(candidates)} candidates with Xray...")
    
    # Batasi concurrency. Jangan terlalu barbar ke server dauslearn
    sem = asyncio.Semaphore(25) 
    tasks = []
    
    random.shuffle(candidates)
    
    # Cek max 500 sample per jam (untuk keamanan server dauslearn)
    for i, p in enumerate(candidates[:500]): 
        async def wrapped_check(proxy, idx):
            async with sem:
                return await check_proxy_with_xray(proxy, idx % 60)
        tasks.append(wrapped_check(p, i))
        
    results = await asyncio.gather(*tasks)
    active_proxies = [r for r in results if r]
    
    print(f"✅ VERIFIED Tunnel Proxies: {len(active_proxies)}")

    with open("active_proxies.txt", "w") as f:
        f.write("\n".join(active_proxies))

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
