import os
import sys
import struct
import json
import uuid
import time

CLIENT_ID = "1538504622652661830"
OP_HANDSHAKE = 0
OP_FRAME = 1
OP_CLOSE = 2

def test_discord():
    print("Testing Discord IPC Named Pipe Connection on Windows...")
    pipe_handle = None
    pipe_index = -1
    for i in range(10):
        pipe_path = f"\\\\.\\pipe\\discord-ipc-{i}"
        try:
            f = open(pipe_path, "r+b", buffering=0)
            pipe_handle = f
            pipe_index = i
            print(f"Connected to {pipe_path}!")
            break
        except Exception as e:
            continue

    if not pipe_handle:
        print("ERROR: Discord is not running or no discord-ipc pipe is available.")
        return

    # Handshake
    hs_payload = json.dumps({"v": 1, "client_id": CLIENT_ID}).encode("utf-8")
    pipe_handle.write(struct.pack("<II", OP_HANDSHAKE, len(hs_payload)) + hs_payload)
    pipe_handle.flush()

    # Read Handshake Reply
    header = pipe_handle.read(8)
    if len(header) < 8:
        print("Failed to read header.")
        return
    op, length = struct.unpack("<II", header)
    reply = pipe_handle.read(length).decode("utf-8", errors="ignore")
    print(f"Handshake Reply (op={op}, len={length}):\n{reply}\n")

    # Send Activity Frame with HTTPS image and assets
    activity = {
        "cmd": "SET_ACTIVITY",
        "args": {
            "pid": os.getpid(),
            "activity": {
                "details": "Testing VayuClient Discord RPC",
                "state": "Main Menu",
                "timestamps": {
                    "start": int(time.time() * 1000)
                },
                "assets": {
                    "large_image": "vayu_logo",
                    "large_text": "VayuClient v1.8.0",
                    "small_image": "vayu_logo",
                    "small_text": "Developer: ANSH9BOSS"
                },
                "buttons": [
                    {
                        "label": "Join Discord",
                        "url": "https://discord.gg/RGzATq3v7J"
                    }
                ]
            }
        },
        "nonce": str(uuid.uuid4())
    }

    frame_payload = json.dumps(activity).encode("utf-8")
    pipe_handle.write(struct.pack("<II", OP_FRAME, len(frame_payload)) + frame_payload)
    pipe_handle.flush()

    # Read Frame Reply
    header = pipe_handle.read(8)
    if len(header) == 8:
        op, length = struct.unpack("<II", header)
        reply = pipe_handle.read(length).decode("utf-8", errors="ignore")
        print(f"SetActivity Reply (op={op}, len={length}):\n{reply}\n")

    pipe_handle.close()

if __name__ == "__main__":
    test_discord()
