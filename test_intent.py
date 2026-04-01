#!/usr/bin/env python3
"""测试豆包 LLM 意图分析 - 使用 Responses API"""

import json
import requests

# ====== 配置 ======
API_KEY = "01478e14-a694-4691-9464-aa7768b692f4"
MODEL = "doubao-seed-2-0-mini-260215"
API_URL = "https://ark.cn-beijing.volces.com/api/v3/responses"

# 模拟已配置的功能列表
FUNCTIONS = [
    {"id": "feishu_todo", "name": "记录待办", "description": "发送到飞书创建待办事项"},
    {"id": "flash_note", "name": "闪记", "description": "快速记录，发送到指定URL"},
]

SYSTEM_PROMPT = None  # 延迟构建

def build_system_prompt():
    function_list = "\n".join(
        f"{i+1}. {f['id']} - {f['name']}: {f['description']}"
        for i, f in enumerate(FUNCTIONS)
    )
    return f"""你是一个语音指令分类助手。用户会说一句话，你需要判断用户的意图最可能对应哪些功能。

可用功能列表:
{function_list}

请返回最匹配的功能（最多3个），按匹配度从高到低排列。严格按以下JSON格式返回，不要有其他内容:
[
  {{"function_id": "功能id", "confidence": 0.95, "parsed_content": "提取的核心内容"}},
  {{"function_id": "功能id", "confidence": 0.7, "parsed_content": "提取的核心内容"}}
]

注意:
- confidence 是 0-1 之间的浮点数
- parsed_content 是从用户话语中提取出的与该功能相关的核心内容
- 如果没有任何功能匹配，返回空数组 []"""


def match_functions(input_text: str):
    system_prompt = build_system_prompt()

    body = {
        "model": MODEL,
        "input": [
            {"role": "system", "content": [{"type": "input_text", "text": system_prompt}]},
            {"role": "user", "content": [{"type": "input_text", "text": input_text}]},
        ],
    }

    print(f"\n>>> 输入: \"{input_text}\"")

    resp = requests.post(
        API_URL,
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
        },
        json=body,
        timeout=30,
    )

    if resp.status_code != 200:
        print(f"!!! 错误 {resp.status_code}: {resp.text}")
        return

    data = resp.json()
    # Responses API: output[].content[].text
    output = data.get("output", [])
    content = ""
    for item in output:
        if item.get("type") == "message":
            for c in item.get("content", []):
                if c.get("type") == "output_text":
                    content = c["text"]

    if not content:
        print(f"!!! 未获取到内容, 原始响应: {json.dumps(data, ensure_ascii=False, indent=2)}")
        return

    print(f"<<< LLM 返回:\n{content}\n")

    # 解析
    clean = content.replace("```json", "").replace("```", "").strip()
    try:
        matches = json.loads(clean)
        print("=== 匹配结果 ===")
        for m in matches:
            name = next((f["name"] for f in FUNCTIONS if f["id"] == m["function_id"]), m["function_id"])
            print(f"  {name}  {int(m['confidence']*100)}%  内容: \"{m['parsed_content']}\"")
    except json.JSONDecodeError as e:
        print(f"!!! JSON 解析失败: {e}")


if __name__ == "__main__":
    test_inputs = [
        "帮我记一下明天下午三点开会",
        "记录一下刚才的想法，关于产品改版方案",
        "提醒我周五之前交报告",
    ]

    for text in test_inputs:
        match_functions(text)
        print()
