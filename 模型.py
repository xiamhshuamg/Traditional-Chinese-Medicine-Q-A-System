import requests
import json

API_KEY = "7c3632d6fad4489ca69c4626714459ed.a8uz4uvAReIfp5lO"  # 替换成你的实际API Key
API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"


def recommend_prescription(symptom):
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "glm-4",  # 使用GLM-4模型
        "messages": [
            {"role": "system", "content": "你是中医专家，根据症状推荐中药方剂，考虑配伍禁忌。"},
            {"role": "user", "content": f"症状：{symptom}。推荐处方，并检查禁忌。"}
        ],
        "temperature": 0.7
    }

    response = requests.post(API_URL, headers=headers, json=payload)
    if response.status_code == 200:
        result = response.json()['choices'][0]['message']['content']
        return result
    else:
        return f"API调用失败，状态码: {response.status_code}, 错误: {response.text}"


# 测试
print(recommend_prescription("头痛发热"))