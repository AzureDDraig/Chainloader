import os

log_path = r"C:\Users\Ddraig__\.gemini\antigravity\brain\1f8b8659-9548-4a14-8847-164b57a343fd\.system_generated\tasks\task-5088.log"
if os.path.exists(log_path):
    with open(log_path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if "Posting setup events to" in line:
                print(line.strip())
else:
    print("Log not found")
