#!/usr/bin/env python3
"""Generate synthetic, demo-only FIX 4.2 traffic captures for Indian and US markets.

All data is randomly fabricated for local demonstration - no real sessions,
orders, clients or counterparties are represented. Safe to commit / upload.

Outputs (same envelope format FixTrafficParser.java understands):
  samples/Fix_traffic_demo_india.txt
  samples/Fix_traffic_demo_us.txt
"""

import random
from datetime import datetime, timedelta
from collections import Counter
import re

BEGINS = "FIX.4.2"

MARKET_PROFILES = {
    "india": {
        "file": "samples/Fix_traffic_demo_india.txt",
        "seed": 20250617,
        "base_time": datetime(2025, 6, 17, 9, 15, 0),
        "currency": "INR",
        "id_suffix": "20250617",
        "host": "DemoHost1",
        "plugin": "DemoFixGateway(PARTNER1)",
        "pid": 1001,
        "client_comp": "NSEBRK01",   # app-side order sender (49 on inbound messages)
        "receiver_comp": "MGATE01",  # counterparty / execution side (49 on outbound)
        "account": "DMO0001",        # tag 1
        "target_sub": "DEMOROUTE",   # tag 57
        "on_behalf": "DEMOGW",       # tag 115
        "on_behalf_sub": "U1",       # tag 116
        "symbols": ["RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "SBIN",
                    "BHARTIARTL", "ITC", "LUPIN", "TATAMOTORS", "AXISBANK", "SUNPHARMA"],
        "options": ["NIFTY25JUN24500CE", "NIFTY25JUN24500PE", "BANKNIFTY25JUN52000CE"],
        "mid": 24000.0,
        "qty_pool": [10, 25, 50, 75, 100, 150, 250],
        "sec_id": "DEMOIN01",
    },
    "us": {
        "file": "samples/Fix_traffic_demo_us.txt",
        "seed": 20250618,
        "base_time": datetime(2025, 6, 18, 9, 30, 0),  # US cash open
        "currency": "USD",
        "id_suffix": "20250618",
        "host": "DemoHost2",
        "plugin": "DemoFixGateway(PARTNER2)",
        "pid": 1002,
        "client_comp": "USBRK01",
        "receiver_comp": "EXEGATE",
        "account": "DMO0002",
        "target_sub": "USROUTE",
        "on_behalf": "USGW",
        "on_behalf_sub": "U2",
        "symbols": ["AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "GOOGL", "META",
                    "JPM", "V", "NFLX", "CRM", "AMD"],
        "options": ["SPY25JUN28C600", "SPY25JUN28P600", "QQQ25JUN27C520"],
        "mid": 31000.0,
        "qty_pool": [50, 100, 200, 300, 500, 750, 1000],
        "sec_id": "DEMOUS01",
    },
}

DOW = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]


def envelope(when, dow, seq, direction, body, host, plugin, pid):
    return (f"{host} {dow} {when.strftime('%b %d %H:%M:%S %Y')} GMT   "
            f"{seq:06d} {plugin} [{pid}] < {direction} > {body}")


def body(fields):
    begin = f"8={fields[0][1]}"
    parts = [f"{t}={v}" for t, v in fields[1:]]
    joined = "|".join(parts)
    length = len(joined) + 1
    full = begin + "|" + f"9={length}|" + joined + "|"
    checksum = sum(ord(c) for c in full) % 256
    return full + f"10={checksum:03d}|"


def sending_time(when, ms=False):
    base = when.strftime("%Y%m%d-%H:%M:%S")
    return base + f".{when.microsecond // 1000:03d}" if ms else base


def build_header(seq, direction, mt, extra, when, ms, env):
    client, receiver = env["client_comp"], env["receiver_comp"]
    sender = (client, receiver) if direction == "in" else (receiver, client)
    standard = [(8, BEGINS), (35, mt), (34, seq), (49, sender[0])]
    return standard + extra + [(52, sending_time(when, ms)), (56, sender[-1])]


def generate(cfg):
    random.seed(cfg["seed"])
    lines = []
    seq_in = 1
    seq_out = 1
    t = cfg["base_time"]
    order_no = 0
    exec_no = 0
    market_dow = DOW[cfg["base_time"].weekday()]
    suffix = cfg["id_suffix"]
    symbols = cfg["symbols"]
    options = cfg["options"]
    cur = cfg["currency"]
    mid = cfg["mid"]
    qty_pool = cfg["qty_pool"]
    sec_id = cfg["sec_id"]
    env = cfg  # holds host/plugin/pid/comp-ids/account/sub-ids

    lines.append(envelope(t, market_dow, random.randint(100000, 999999), "evt",
                          "Fix engine connection established", env["host"], env["plugin"], env["pid"]))
    lines.append(envelope(t, market_dow, seq_in, "in",
                          body(build_header(seq_in, "in", "A", [(98, "0"), (108, "30"), (141, "Y"), (57, "ADMIN")], t, False, env)), env["host"], env["plugin"], env["pid"]))
    seq_in += 1
    lines.append(envelope(t, market_dow, seq_out, "out",
                          body(build_header(seq_out, "out", "A", [(98, "0"), (108, "30"), (141, "Y"), (57, "ADMIN")], t, True, env)), env["host"], env["plugin"], env["pid"]))
    seq_out += 1

    end = cfg["base_time"] + timedelta(hours=2, minutes=45)
    trading = cfg["base_time"]
    hb_step = timedelta(seconds=30)

    while trading < end:
        trading += hb_step
        lines.append(envelope(trading, market_dow, seq_in, "in",
                              body(build_header(seq_in, "in", "0", [(57, "ADMIN")], trading, False, env)), env["host"], env["plugin"], env["pid"]))
        seq_in += 1
        lines.append(envelope(trading, market_dow, seq_out, "out",
                              body(build_header(seq_out, "out", "0", [(57, "ADMIN")], trading, True, env)), env["host"], env["plugin"], env["pid"]))
        seq_out += 1

        if random.random() < 0.45:
            if random.random() < 0.55:
                lines.append(envelope(trading, market_dow, seq_in, "in",
                                      body(build_header(seq_in, "in", "1", [(57, "ADMIN")], trading, False, env)), env["host"], env["plugin"], env["pid"]))
                seq_in += 1

            symbol = random.choice(symbols + options)
            side = "1" if random.random() < 0.6 else "2"
            qty = random.choice(qty_pool)
            price = round(mid + random.uniform(-50, 50), 2)
            clord_base = f"DMORDR{order_no + 1:04d}-{suffix}"
            acct = env["account"]
            tsub = env["target_sub"]
            ob = env["on_behalf"]
            obs = env["on_behalf_sub"]

            if random.random() < 0.15:
                order_no += 1
                ordid = f"{suffix}{order_no:08d}"
                lines.append(envelope(trading, market_dow, seq_in, "in",
                                      body(build_header(seq_in, "in", "F",
                                          [(11, clord_base), (37, ordid), (41, clord_base), (54, side),
                                           (55, symbol), (38, str(qty)), (1, acct), (15, cur),
                                           (60, sending_time(trading)), (57, tsub)], trading, False, env)), env["host"], env["plugin"], env["pid"]))
                seq_in += 1
                exec_no += 1
                lines.append(envelope(trading, market_dow, seq_out, "out",
                                      body(build_header(seq_out, "out", "8",
                                          [(11, clord_base), (37, ordid), (17, f"DEMO{exec_no:05d}"),
                                           (150, "4"), (39, "4"), (54, side), (38, str(qty)),
                                           (55, symbol), (31, "0"), (151, "0"), (14, "0"), (6, "0"),
                                           (44, str(price)), (1, acct), (15, cur), (60, sending_time(trading))], trading, True, env)), env["host"], env["plugin"], env["pid"]))
                seq_out += 1
            elif random.random() < 0.2:
                order_no += 1
                ordid = f"{suffix}{order_no:08d}"
                new_qty = qty + random.choice([25, 50, 100])
                rep_clord = f"DMORDRR{order_no:04d}-{suffix}"
                lines.append(envelope(trading, market_dow, seq_in, "in",
                                      body(build_header(seq_in, "in", "G",
                                          [(11, rep_clord), (37, ordid), (41, clord_base), (54, side),
                                           (55, symbol), (38, str(new_qty)), (1, acct), (15, cur),
                                           (60, sending_time(trading)), (57, tsub)], trading, False, env)), env["host"], env["plugin"], env["pid"]))
                seq_in += 1
                exec_no += 1
                lines.append(envelope(trading, market_dow, seq_out, "out",
                                      body(build_header(seq_out, "out", "8",
                                          [(11, rep_clord), (37, ordid), (17, f"DEMO{exec_no:05d}"),
                                           (150, "5"), (39, "5"), (54, side), (38, str(new_qty)),
                                           (55, symbol), (31, "0"), (151, str(new_qty)), (14, "0"),
                                           (6, "0"), (44, str(price)), (1, acct), (15, cur),
                                           (60, sending_time(trading))], trading, True, env)), env["host"], env["plugin"], env["pid"]))
                seq_out += 1
            else:
                order_no += 1
                ordid = f"{suffix}{order_no:08d}"
                lines.append(envelope(trading, market_dow, seq_in, "in",
                                      body(build_header(seq_in, "in", "D",
                                          [(11, clord_base), (1, acct), (15, cur), (21, "3"), (22, "5"),
                                           (37, ordid), (38, str(qty)), (48, sec_id), (54, side), (55, symbol),
                                           (40, "2"), (44, str(price)), (59, "1"), (60, sending_time(trading)),
                                           (57, tsub), (115, ob), (116, obs),
                                           (9200, random.choice(["0", "1"])),
                                           (9300, f"{random.random():.8f}"),
                                           (9303, str(random.randint(1000000000, 1999999999)) + ".12345678")], trading, False, env)), env["host"], env["plugin"], env["pid"]))
                seq_in += 1
                exec_no += 1
                partial = qty // random.choice([2, 3])
                lines.append(envelope(trading, market_dow, seq_out, "out",
                                      body(build_header(seq_out, "out", "8",
                                          [(11, clord_base), (37, ordid), (17, f"DEMO{exec_no:05d}"),
                                           (150, "1"), (39, "1"), (54, side), (38, str(qty)), (55, symbol),
                                           (31, str(price)), (151, str(qty - partial)), (14, str(partial)),
                                           (6, str(round(price, 2))), (44, str(price)), (1, acct),
                                           (15, cur), (60, sending_time(trading))], trading, True, env)), env["host"], env["plugin"], env["pid"]))
                seq_out += 1
                exec_no += 1
                lines.append(envelope(trading, market_dow, seq_out, "out",
                                      body(build_header(seq_out, "out", "8",
                                          [(11, clord_base), (37, ordid), (17, f"DEMO{exec_no:05d}"),
                                           (150, "2"), (39, "2"), (54, side), (38, str(qty)), (55, symbol),
                                           (31, str(price)), (151, "0"), (14, str(qty)), (6, str(round(price, 2))),
                                           (44, str(price)), (1, acct), (15, cur), (60, sending_time(trading))], trading, True, env)), env["host"], env["plugin"], env["pid"]))
                seq_out += 1

    with open(cfg["file"], "w") as f:
        f.write("\n".join(lines) + "\n")

    mt = Counter()
    for l in lines:
        m = re.search(r"\b35=([A-Z0-9])\|", l)
        if m:
            mt[m.group(1)] += 1
    print(f"wrote {cfg['file']}: {len(lines)} lines | types: {dict(mt.most_common())}")


for cfg in MARKET_PROFILES.values():
    generate(cfg)