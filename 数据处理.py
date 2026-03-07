# -*- coding: utf-8 -*-
"""
更稳健的数据处理脚本（保持你原有逻辑与接口）
- 生成 nodes.csv / edges.csv（UTF-8-SIG）
- 仅四类边：症状→证型(关联)、证型→方剂(治疗)、无证型时主诉→方剂(治疗)、方剂→中药(包含)
"""
import json, re, csv, logging, os, argparse
from typing import Dict, List, Tuple

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

FIELD_MARKERS = {'主诉': '主诉:', '现病史': '现病史:', '体格检查': '体格检查:'}
OUTPUT_KEYS = ['证型', '治法', '方剂', '中药', '西医诊断', '诊断', '治疗方案']

RE_KV = re.compile(r'(' + '|'.join(map(re.escape, OUTPUT_KEYS)) + r')[:：]')
DOSE_PAT = re.compile(r'(?P<name>[\u4e00-\u9fa5]{1,8})\s*(?P<num>\d+(?:[\.．]\d+)?)\s*(?P<unit>g|克|mg|毫克|kg|千克)?')
FORMULA_SUFFIX = tuple(list("汤散丸饮丹膏露片剂煎合"))
RE_CN_TOKEN = re.compile(r'[\u4e00-\u9fa5]{2,}')

DESC_WORDS = {'一般','迁延','阵发','高发','得温','遇冷','发作','缓解','加重','持续','近期','频发','无明显变化'}
SYM_BLACKLIST = {'病史','体格检查','主诉','现病史','春季','换季','舌','舌质','脉','苔','色淡','色红','苔薄','苔黄','苔白','厚','腻','白','中等','正常','偏瘦','微胖','表情丰富','表情较少','有神','无神','壮实','肥胖','面色','面色白','面色黄','面色红'}
SYNONYM = {'关节痛':'关节疼痛','舌苔白':'白苔'}

def normalize(text: str) -> str:
    return text.replace('．','.' ).replace('\u3000',' ').strip()

def to_grams(num_str: str, unit: str) -> float:
    num = float(num_str.replace('．','.'))
    if not unit or unit in ('g','克'): return num
    u = unit.lower()
    if u in ('mg','毫克'): return num/1000.0
    if u in ('kg','千克'): return num*1000.0
    return num

def slice_sections_by_markers(text: str) -> Dict[str, str]:
    text = normalize(text)
    out: Dict[str, str] = {}
    indices = [(m.start(), m.end(), m.group(1)) for m in RE_KV.finditer(text)]
    if not indices: return out
    for i, (s, e, key) in enumerate(indices):
        next_s = indices[i+1][0] if i+1 < len(indices) else len(text)
        seg = text[e:next_s].strip().lstrip('：:，,。；; \n\r\t')
        out[key] = seg
    return out

def extract_section(full_text: str, marker: str, following: List[str]) -> str:
    start = full_text.find(marker)
    if start == -1: return ''
    s = start + len(marker)
    end = len(full_text)
    for m in following:
        if m == marker: continue
        i = full_text.find(m, s)
        if i != -1 and i < end: end = i
    return full_text[s:end].strip()

def tokens_from_chinese(text: str) -> List[str]:
    if not text: return []
    terms = RE_CN_TOKEN.findall(text)
    terms = [t for t in terms if t not in SYM_BLACKLIST]
    return list(set(terms))

def extract_symptoms_from_field(field_content: str) -> List[str]:
    field_content = normalize(field_content)
    field_content = re.sub(r'\([^)]*(?:' + '|'.join(DESC_WORDS) + r')[^)]*\)', '', field_content)
    main_text = re.sub(r'\([^)]+\)', '', field_content)
    cand = tokens_from_chinese(main_text)
    for detail in re.findall(r'\(([^)]+)\)', field_content):
        if any(w in detail for w in DESC_WORDS): continue
        cand += tokens_from_chinese(detail)
    cand = list(set(cand))
    return [SYNONYM.get(c, c) for c in cand]

def parse_output(output_text: str) -> Dict[str, str]:
    out = slice_sections_by_markers(output_text or '')
    return {'证型': out.get('证型','').strip(),
            '方剂': out.get('方剂','').strip(),
            '中药': out.get('中药','').strip()}

def extract_syndromes(section_text: str) -> List[str]:
    if not section_text: return []
    segs = [s.strip() for s in re.split(r'[、，,；;。\s]+', section_text) if s.strip()]
    clean = []
    for s in segs:
        if re.search(r'\d', s): continue
        if '方剂' in s or '中药' in s: continue
        if RE_CN_TOKEN.fullmatch(s) and 2 <= len(s) <= 10: clean.append(s)
    return list(dict.fromkeys(clean))

def extract_formulas(section_text: str) -> List[str]:
    if not section_text: return []
    section_text = re.sub(r'（[^）]*）|\([^)]+\)', '', section_text)
    segs = re.split(r'[、，,；;。\s]+', section_text)
    out = []
    for p in segs:
        p = p.strip()
        if not p or re.search(r'\d', p): continue
        if not RE_CN_TOKEN.search(p): continue
        if p.endswith(FORMULA_SUFFIX) or (2 <= len(p) <= 12): out.append(p)
    return list(dict.fromkeys(out))

def extract_herbs_with_dose(section_text: str) -> Dict[str, float]:
    herbs = {}
    if not section_text: return herbs
    for d in DOSE_PAT.finditer(normalize(section_text)):
        name, num, unit = d.group('name'), d.group('num'), d.group('unit') or ''
        try:
            g = to_grams(num, unit)
        except ValueError:
            continue
        if len(name) >= 2:
            herbs[name] = g
    return herbs

def build_graph(records: List[Dict]) -> Tuple[List[Dict], List[Dict]]:
    nodes, edges = [], []
    node_map: Dict[Tuple[str,str], str] = {}
    node_id = 0
    def get_node_id(label: str, name: str, source: str=None, dose: float=None):
        nonlocal node_id
        key = (label, name)
        if key not in node_map:
            nid = f"{label}_{node_id}"
            node_id += 1
            node = {'id': nid, '标签': label, '名称': name}
            if source: node['来源'] = source
            if dose is not None: node['剂量'] = dose
            nodes.append(node)
            node_map[key] = nid
        return node_map[key]

    for idx, entry in enumerate(records):
        logger.info(f"处理第 {idx+1}/{len(records)} 条")
        input_text  = entry.get('input','') or ''
        output_text = entry.get('output','') or ''
        symptoms = []
        for src, marker in FIELD_MARKERS.items():
            section = extract_section(input_text, marker, list(FIELD_MARKERS.values()))
            if not section: continue
            ss = extract_symptoms_from_field(section)
            symptoms.extend([(s, src) for s in ss])
        seen = set()
        symptoms = [(s, src) for s, src in symptoms if not (s,src) in seen and not seen.add((s,src))]

        parsed = parse_output(output_text)
        syndromes = extract_syndromes(parsed.get('证型',''))
        formulas  = extract_formulas(parsed.get('方剂',''))
        herbs_map = extract_herbs_with_dose(parsed.get('中药',''))

        symptom_ids = [(get_node_id('症状', s, source=src), src) for s, src in symptoms]
        syndrome_ids = [get_node_id('证型', z) for z in syndromes]
        formula_ids  = [get_node_id('方剂', f) for f in formulas]
        herb_ids     = [get_node_id('中药', h, dose=herbs_map[h]) for h in herbs_map]

        for z in syndrome_ids:
            for sid, src in symptom_ids:
                w = 1.0 if src == '主诉' else 0.5
                edges.append({'source': sid, 'target': z, 'type': '关联', 'weight': w})
        for z in syndrome_ids:
            for f in formula_ids:
                edges.append({'source': z, 'target': f, 'type': '治疗', 'weight': 1.0})
        if not syndrome_ids and formula_ids:
            for sid, src in symptom_ids:
                if src == '主诉':
                    for f in formula_ids:
                        edges.append({'source': sid, 'target': f, 'type': '治疗', 'weight': 1.0})
        for f in formula_ids:
            for h in herb_ids:
                edges.append({'source': f, 'target': h, 'type': '包含', 'weight': 1.0})
    return nodes, edges

def save_to_csv(nodes, edges, nodes_file='nodes.csv', edges_file='edges.csv'):
    with open(nodes_file, 'w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['id','标签','名称','来源','剂量'])
        writer.writeheader(); writer.writerows(nodes)
    with open(edges_file, 'w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['source','target','type','weight'])
        writer.writeheader(); writer.writerows(edges)
    logger.info(f"写出：{nodes_file}（{len(nodes)} 节点），{len(edges)} 条边 -> {edges_file}）")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--json', default='data_random_half12.json', help='输入 JSON（数组）')
    ap.add_argument('--nodes', default='nodes.csv', help='输出节点 CSV')
    ap.add_argument('--edges', default='edges.csv', help='输出边 CSV')
    args = ap.parse_args()
    with open(args.json, 'r', encoding='utf-8') as f:
        data = json.load(f)
    nodes, edges = build_graph(data)
    save_to_csv(nodes, edges, args.nodes, args.edges)
    print(f"完成：节点 {len(nodes)}，边 {len(edges)}")
    print(f"输出：{os.path.abspath(args.nodes)} | {os.path.abspath(args.edges)}")

if __name__ == '__main__':
    main()
