# -*- coding: utf-8 -*-
"""
知识图谱.py
- 从 nodes_enriched.csv / edges_enriched.csv 导入 Neo4j
- 每 500 条输出一次进度：已加载多少节点/关系
- BOM 安全读取、自动去除列名 BOM
- 为常见标签创建 id 唯一约束（存在则跳过）
- 使用 graph.commit(tx) 避免 transaction.commit 弃用警告
- 可通过命令行参数自定义文件路径/批大小
"""

import os
import time
import argparse
from typing import Dict, Any

import pandas as pd
from py2neo import Graph


def read_csv_clean(path: str) -> pd.DataFrame:
    """BOM 安全读取 CSV，并清理列名中的 BOM/空白。"""
    last_err = None
    for enc in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            df = pd.read_csv(path, encoding=enc)
            break
        except Exception as e:
            last_err = e
            continue
    else:
        raise RuntimeError(f"读取 {path} 失败：{last_err}")

    df.columns = [str(c).replace("\ufeff", "").strip() for c in df.columns]
    return df


def ensure_constraints(g: Graph):
    """为常见标签创建 id 唯一约束（存在则跳过）。"""
    labels = ["症状", "证型", "方剂", "中药", "草本属性", "香草味"]
    for lbl in labels:
        try:
            g.run(
                f"CREATE CONSTRAINT {lbl}_id_unique IF NOT EXISTS "
                f"FOR (n:`{lbl}`) REQUIRE n.id IS UNIQUE"
            )
        except Exception as e:
            print(f"约束创建失败({lbl}): {e}")


def sanitize_rel_type(reltype: str) -> str:
    """关系类型去掉空格；其余保持原样（Neo4j 允许中文和下划线，但不允许空格）。"""
    return (reltype or "关联").replace(" ", "")


def load_nodes(g: Graph, nodes_path: str, batch: int = 500) -> int:
    print(f"开始加载节点: {nodes_path}")
    df = read_csv_clean(nodes_path)

    if "id" not in df.columns:
        raise ValueError(f"节点文件缺少 id 列，当前列名={df.columns.tolist()}")

    # 清洗 id
    df["id"] = df["id"].astype(str).str.strip()
    df = df.loc[df["id"].ne("") & df["id"].notna()].copy()

    total = len(df)
    if total == 0:
        print("⚠️ 节点文件为空或全部无效 id。")
        return 0

    tx = g.begin()
    count = 0

    for i, row in df.iterrows():
        label = row.get("标签", "节点") or "节点"
        nid = str(row.get("id")).strip()
        name = str(row.get("名称") or "无名称").strip()

        # 其他字段作为属性
        props: Dict[str, Any] = {
            k: v for k, v in row.items()
            if k not in ("id", "标签", "名称") and pd.notna(v) and str(v) != ""
        }

        cypher = "MERGE (n:`%s` {id: $id}) SET n.名称 = $name SET n += $props" % label
        tx.run(cypher, id=nid, name=name, props=props)
        count += 1

        # 每 batch 条提交并打印进度
        if count % batch == 0:
            g.commit(tx)
            print(f"✅ 已加载节点 {count}/{total}")
            tx = g.begin()

    # 提交剩余
    g.commit(tx)
    print(f"✅ 节点加载完成，共 {count} 个（总计 {total}）")
    return count


def load_edges(g: Graph, edges_path: str, batch: int = 500) -> int:
    if not os.path.exists(edges_path):
        print(f"关系文件未找到: {edges_path}")
        return 0

    print(f"开始加载关系: {edges_path}")
    df = read_csv_clean(edges_path)

    # 必要列校验
    for c in ("source", "target", "type"):
        if c not in df.columns:
            raise ValueError(f"边文件缺少必要列: {c}，当前列名={df.columns.tolist()}")

    # 统一 & 清洗
    df["source"] = df["source"].astype(str).str.strip()
    df["target"] = df["target"].astype(str).str.strip()
    df["type"] = df["type"].astype(str).map(sanitize_rel_type)
    if "weight" not in df.columns:
        df["weight"] = 1.0

    # 过滤掉空的 source/target
    df = df.loc[df["source"].ne("") & df["target"].ne("")].copy()
    total = len(df)
    if total == 0:
        print("边文件为空或全部无效 source/target。")
        return 0

    # 提前抓取现有 id，进行存在性预检（避免无意义 MATCH）
    existing_ids = set(
        g.run("MATCH (n) RETURN n.id AS id").to_series().dropna().astype(str)
    )
    missing_src = df.loc[~df["source"].isin(existing_ids)]
    missing_tgt = df.loc[~df["target"].isin(existing_ids)]
    if not missing_src.empty or not missing_tgt.empty:
        print(f"发现 {len(missing_src)} 条记录的 source 或 "
              f"{len(missing_tgt)} 条记录的 target 在图中不存在，这些边将被跳过：")
        print(missing_src.head(3)[["source", "target", "type"]])
        print(missing_tgt.head(3)[["source", "target", "type"]])

    # 保留图中存在的节点的边
    df = df.loc[df["source"].isin(existing_ids) & df["target"].isin(existing_ids)].copy()
    total = len(df)
    if total == 0:
        print("过滤后无可写入的关系。")
        return 0

    tx = g.begin()
    count = 0

    for i, r in df.iterrows():
        rel_type = r["type"]
        s = r["source"]
        t = r["target"]
        try:
            w = float(r.get("weight", 1.0))
        except Exception:
            w = 1.0

        # 其他列作为关系属性（排除固定列）
        props: Dict[str, Any] = {
            k: r[k] for k in r.index
            if k not in ("source", "target", "type", "weight") and pd.notna(r[k]) and str(r[k]) != ""
        }
        props["weight"] = w

        cypher = (
            f"MATCH (a {{id: $s}}), (b {{id: $t}}) "
            f"MERGE (a)-[r:`{rel_type}`]->(b) "
            f"SET r += $props"
        )
        tx.run(cypher, s=s, t=t, props=props)
        count += 1

        # 每 batch 条提交并打印进度
        if count % batch == 0:
            g.commit(tx)
            print(f"✅ 已加载关系 {count}/{total}")
            tx = g.begin()

    # 提交剩余
    g.commit(tx)
    print(f"✅ 关系加载完成，共 {count} 条（总计 {total}）")
    return count


def main():
    parser = argparse.ArgumentParser(description="把 CSV 导入 Neo4j，并每 500 条输出一次进度。")
    parser.add_argument("--nodes", default="nodes_enriched.csv", help="节点 CSV（默认 nodes_enriched.csv）")
    parser.add_argument("--edges", default="edges_enriched.csv", help="关系 CSV（默认 edges_enriched.csv）")
    parser.add_argument("--data-dir", default="F:\\TCM\\data", help="数据目录（默认 F:\\TCM\\data）")
    parser.add_argument("--batch", type=int, default=500, help="提交与进度输出的批大小（默认 500）")
    parser.add_argument("--password", default="959946...wjq", help="Neo4j 密码（默认与原脚本一致）")
    args = parser.parse_args()

    # 允许既支持 data 目录也支持当前目录文件
    base_dir = args.data_dir if os.path.isdir(args.data_dir) else os.getcwd()
    nodes_path = os.path.join(base_dir, args.nodes) if not os.path.isabs(args.nodes) else args.nodes
    edges_path = os.path.join(base_dir, args.edges) if not os.path.isabs(args.edges) else args.edges

    print("等待 Neo4j 服务启动...")
    time.sleep(3)

    # 连接 Neo4j（先 bolt，失败再 http）
    try:
        g = Graph("bolt://localhost:7687", auth=("neo4j", args.password))
        g.run("RETURN 1")
        print("✅ Neo4j 连接成功 (bolt)")
    except Exception:
        g = Graph("http://localhost:7474", auth=("neo4j", args.password))
        g.run("RETURN 1")
        print("✅ Neo4j 连接成功 (http)")

    # 约束
    ensure_constraints(g)

    # 导入
    print("开始构建知识图谱…")
    n = load_nodes(g, nodes_path, batch=args.batch)
    e = load_edges(g, edges_path, batch=args.batch)

    # 建议：建立简单的可视化辅助属性
    if e > 0:
        print("为所有节点计算 degree（用于可视化大小映射）...")
        g.run("""
        MATCH (n)
        WITH n, count { (n)--() } AS d
        SET n.degree = d
        """)

    print(f"🎉 完成。共导入 节点 {n} 个，关系 {e} 条。")
    print("在 Neo4j Browser 可执行：MATCH (n) RETURN n LIMIT 100")


if __name__ == "__main__":
    main()
