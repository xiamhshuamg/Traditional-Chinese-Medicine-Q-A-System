import json
import random


def remove_random_half_json(input_file, output_file):
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    if isinstance(data, list):
        # 随机打乱并取一半
        random.shuffle(data)
        new_data = data[:len(data) // 2]

    elif isinstance(data, dict):
        keys = list(data.keys())
        random.shuffle(keys)
        keys_to_keep = keys[:len(keys) // 2]
        new_data = {k: data[k] for k in keys_to_keep}

    else:
        print("不支持的 JSON 格式")
        return

    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(new_data, f, ensure_ascii=False, indent=2)

    print(f"随机删除一半数据完成")
    print(f"已保存到: {output_file}")


# 使用示例
remove_random_half_json('data_random_half11.json', 'data_random_half12.json')