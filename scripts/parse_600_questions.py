"""
Parse bộ 600 câu hỏi sát hạch lái xe từ PDF → JSON (v4).
- extract_text_lines() for structured text with coordinates
- Underline rects to detect correct answers
- Cross-page carry-over for questions spanning pages
"""
import json, re, sys
from pathlib import Path
from collections import defaultdict
import pdfplumber

PDF_PATH = r"C:\Users\Me\Documents\dts-identity\tmp-download\273963059_Bộ 600 câu hỏi dùng cho sát hạch lái xe cơ giới đường bộ.pdf"
OUTPUT_DIR = Path(r"C:\Users\Me\Documents\dts-dataset\driving-license")

CHAPTER_RANGES = {1: (1, 180), 2: (181, 205), 3: (206, 263), 4: (264, 300), 5: (301, 485), 6: (486, 600)}
CHAPTER_NAMES = {
    1: "Quy định chung và quy tắc giao thông đường bộ",
    2: "Văn hóa giao thông, đạo đức người lái xe, kỹ năng PCCC và cứu hộ, cứu nạn",
    3: "Kỹ thuật lái xe",
    4: "Cấu tạo và sửa chữa",
    5: "Báo hiệu đường bộ",
    6: "Giải thế sa hình và kỹ năng xử lý tình huống giao thông",
}
LABEL_MAP = {1: "A", 2: "B", 3: "C", 4: "D"}


def get_underlines(page):
    return [{"top": r["top"], "bottom": r["bottom"], "x0": r["x0"], "x1": r["x1"]}
            for r in (page.rects or [])
            if r.get("height", 99) < 3 and r.get("non_stroking_color") == 0.0]


def get_text_lines(page):
    lines = []
    for rl in (page.extract_text_lines() or []):
        t = rl.get("text", "").strip()
        if t:
            lines.append({"text": t, "top": rl["top"], "bottom": rl["bottom"], "x0": rl["x0"]})
    return lines


def is_underlined(line, underlines, tol=14):
    """Check if a text line has an underline near its bottom edge."""
    lb = line["bottom"]
    return any(abs(ul["top"] - lb) < tol for ul in underlines)


def detect_chapter(lines):
    txt = " ".join(l["text"] for l in lines)
    for pat, ch in [(r"CHƯƠNG\s+I[\.\s]", 1), (r"CHƯƠNG\s+II[\.\s]", 2),
                    (r"CHƯƠNG\s+III[\.\s]", 3), (r"CHƯƠNG\s+IV[\.\s]", 4),
                    (r"CHƯƠNG\s+V[\.\s]", 5), (r"CHƯƠNG\s+VI[\.\s]", 6)]:
        if re.search(pat, txt):
            return ch
    return None


def get_chapter(q_num, fallback=None):
    for ch, (s, e) in CHAPTER_RANGES.items():
        if s <= q_num <= e:
            return ch
    return fallback


def parse_all_pages(pdf):
    """
    Collect ALL text lines from ALL pages, tagging each line with:
    - page index
    - has_underline flag
    Then parse questions globally (no page boundary issues).
    """
    all_lines = []
    current_chapter = None

    for page_idx, page in enumerate(pdf.pages):
        lines = get_text_lines(page)
        uls = get_underlines(page)

        if not lines:
            continue

        ch = detect_chapter(lines)
        if ch:
            current_chapter = ch

        for line in lines:
            all_lines.append({
                "text": line["text"],
                "top": line["top"],
                "bottom": line["bottom"],
                "x0": line["x0"],
                "has_underline": is_underlined(line, uls),
                "chapter": current_chapter,
                "page": page_idx,
            })

    return all_lines


def merge_continuations(lines):
    """Merge continuation lines into their parent lines."""
    result = []
    for line in lines:
        text = line["text"]
        x0 = line["x0"]

        # Is this a new question or new option?
        is_question = bool(re.match(r"C[uùâầ]u\s+\d+[\.:]", text, re.IGNORECASE))
        is_option = bool(re.match(r"^\d+[\.\)]\s*", text))

        if is_question or is_option:
            # New logical unit
            result.append(dict(line))
        elif result:
            # Continuation of previous line
            result[-1]["text"] += " " + text
            result[-1]["bottom"] = line["bottom"]
            # If the continuation has an underline, propagate it
            if line["has_underline"]:
                result[-1]["has_underline"] = True
        else:
            # First line before any question (page number, chapter header, etc.)
            result.append(dict(line))

    return result


def parse_lines_to_questions(lines):
    """Parse merged lines into question objects."""
    questions = []
    current_q = None
    current_opts = []

    for line in lines:
        text = line["text"]

        qm = re.match(r"C[uùâầ]u\s+(\d+)[\.:]\s*(.*)", text, re.IGNORECASE)
        if qm:
            # Save previous
            if current_q and current_opts:
                _finalize(current_q, current_opts, questions)

            q_num = int(qm.group(1))
            q_text = qm.group(2).strip()
            current_q = {
                "id": q_num,
                "question": q_text,
                "chapter": line.get("chapter") or get_chapter(q_num),
                "is_critical": False,
                "image": None,
            }
            current_opts = []
            continue

        om = re.match(r"^(\d+)[\.\)]\s*(.*)", text)
        if om and current_q:
            opt_num = int(om.group(1))
            opt_text = om.group(2).strip()
            current_opts.append({
                "label_num": opt_num,
                "text": opt_text,
                "is_correct": line.get("has_underline", False),
            })
            continue

        # Continuation text
        if current_q:
            if current_opts:
                current_opts[-1]["text"] += " " + text
            else:
                current_q["question"] += " " + text

    # Last question
    if current_q and current_opts:
        _finalize(current_q, current_opts, questions)

    return questions


def _finalize(q, opts, questions):
    q["question"] = clean_text(q["question"])
    mapped = []
    correct = None
    for o in opts:
        letter = LABEL_MAP.get(o["label_num"], str(o["label_num"]))
        mapped.append({"label": letter, "text": clean_text(o["text"])})
        if o["is_correct"]:
            correct = letter
    q["options"] = mapped
    q["correct_answer"] = correct
    q["explanation"] = None
    if q["chapter"] is None:
        q["chapter"] = get_chapter(q["id"])
    questions.append(q)


def clean_text(text):
    text = re.sub(r"\s+", " ", text).strip()
    # Remove trailing artifacts: page numbers, chapter headers
    text = re.sub(r"\s+\d{1,3}$", "", text)  # Trailing page numbers
    text = re.sub(r"\s*CHƯƠNG\s+[IVX]+\..*$", "", text, flags=re.IGNORECASE)
    text = re.sub(r"\s*\d{1,3}\s*CHƯƠNG", " CHƯƠNG", text)  # "45 CHƯƠNG" pattern
    text = re.sub(r"\s*CHƯƠNG\s+[IVX]+\..*$", "", text, flags=re.IGNORECASE)
    text = text.strip()
    # Remove empty or page-number-only options
    if re.match(r"^\d{1,3}$", text):
        return ""
    return text


# Known correct answers for questions where PDF underline is missing
MANUAL_ANSWERS = {
    204: "A",  # First action when detecting fire: stay calm, pull over, turn off ignition
}


def post_process(questions):
    """Fix known issues, apply manual answers, detect critical questions."""
    for q in questions:
        # Apply manual answers for questions where PDF underline was missing
        if q["id"] in MANUAL_ANSWERS and q["correct_answer"] is None:
            q["correct_answer"] = MANUAL_ANSWERS[q["id"]]

        # Detect critical questions
        t = q["question"].lower()
        if any(k in t for k in ["mất an toàn giao thông nghiêm trọng"]):
            q["is_critical"] = True

    return questions


def validate(questions):
    print(f"\n=== VALIDATION ===")
    print(f"Total: {len(questions)}")

    expected = set(range(1, 601))
    found = {q["id"] for q in questions}
    missing = sorted(expected - found)
    extra = sorted(found - expected)

    if missing:
        print(f"MISSING ({len(missing)}): {missing[:30]}{'...' if len(missing) > 30 else ''}")
    if extra:
        print(f"EXTRA ({len(extra)}): {extra}")

    no_ans = sorted([q["id"] for q in questions if q["correct_answer"] is None])
    if no_ans:
        print(f"NO ANSWER ({len(no_ans)}): {no_ans}")

    chc = defaultdict(int)
    for q in questions:
        chc[q["chapter"]] += 1
    for ch in sorted(chc):
        s, e = CHAPTER_RANGES.get(ch, (0, 0))
        exp = e - s + 1
        ok = "OK" if chc[ch] == exp else f"EXP {exp}"
        print(f"  Ch.{ch}: {chc[ch]} [{ok}]")

    crit = sum(1 for q in questions if q["is_critical"])
    print(f"Critical: {crit}")
    return not missing and not no_ans, missing, no_ans


def save_all(questions, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Full JSON
    p = out_dir / "parsed" / "questions.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(questions, f, ensure_ascii=False, indent=2)
    print(f"[OK] {p} ({len(questions)} questions)")

    # 2. By chapter
    by_ch = defaultdict(list)
    for q in questions:
        by_ch[q["chapter"]].append(q)

    slugs = {1: "chuong-1-quy-dinh-chung", 2: "chuong-2-van-hoa-giao-thong",
             3: "chuong-3-ky-thuat-lai-xe", 4: "chuong-4-cau-tao-va-sua-chua",
             5: "chuong-5-bao-hieu-duong-bo", 6: "chuong-6-sa-hinh-va-xu-ly-tinh-huong"}

    d = out_dir / "parsed" / "by-chapter"
    d.mkdir(parents=True, exist_ok=True)
    for ch, qs in sorted(by_ch.items()):
        fp = d / f"{slugs.get(ch, f'chuong-{ch}')}.json"
        with open(fp, "w", encoding="utf-8") as f:
            json.dump(qs, f, ensure_ascii=False, indent=2)
        print(f"[OK] {fp} ({len(qs)} qs)")

    # 3. Critical
    crit = [q for q in questions if q["is_critical"]]
    cp = out_dir / "parsed" / "critical-questions.json"
    with open(cp, "w", encoding="utf-8") as f:
        json.dump(crit, f, ensure_ascii=False, indent=2)
    print(f"[OK] {cp} ({len(crit)} qs)")

    # 4. Statistics
    stats = {"total": len(questions), "critical": len(crit), "chapters": {},
             "source": "Cuc CSGT - Bo Cong an - 2025", "effective_date": "2025-06-01"}
    for ch, qs in sorted(by_ch.items()):
        s, e = CHAPTER_RANGES[ch]
        stats["chapters"][str(ch)] = {"name": CHAPTER_NAMES[ch], "count": len(qs), "range": f"{s}-{e}"}
    sp = out_dir / "parsed" / "statistics.json"
    with open(sp, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)
    print(f"[OK] {sp}")

    # 5. SQL seed
    sql = out_dir / "sql" / "seed-questions.sql"
    sql.parent.mkdir(parents=True, exist_ok=True)
    with open(sql, "w", encoding="utf-8") as f:
        f.write("-- Seed: 600 cau hoi sat hach lai xe\n-- Nguon: Cuc CSGT - Bo Cong an (2025)\n\nBEGIN;\n\n")
        for q in questions:
            oj = json.dumps(q["options"], ensure_ascii=False)
            qt = q["question"].replace("'", "''")
            ca = q["correct_answer"] or "NULL"
            f.write(f"INSERT INTO questions (id, chapter, question_text, options, correct_answer, is_critical, created_at, updated_at)\n")
            f.write(f"VALUES ({q['id']}, {q['chapter']}, '{qt}', '{oj}'::jsonb, '{ca}', {str(q['is_critical']).upper()}, NOW(), NOW())\n")
            f.write(f"ON CONFLICT (id) DO UPDATE SET question_text=EXCLUDED.question_text, options=EXCLUDED.options, correct_answer=EXCLUDED.correct_answer, is_critical=EXCLUDED.is_critical, updated_at=NOW();\n\n")
        f.write("COMMIT;\n")
    print(f"[OK] {sql}")

    # 6. Copy script
    import shutil
    sd = out_dir / "scripts" / "parse_600_questions.py"
    sd.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(__file__, sd)
    print(f"[OK] {sd}")


if __name__ == "__main__":
    print("=" * 60)
    print("PARSING 600 QUESTIONS (v4 — global merge)")
    print("=" * 60)

    with pdfplumber.open(PDF_PATH) as pdf:
        print(f"Pages: {len(pdf.pages)}")
        all_lines = parse_all_pages(pdf)
        print(f"Raw text lines: {len(all_lines)}")

    merged = merge_continuations(all_lines)
    print(f"After merge: {len(merged)}")

    questions = parse_lines_to_questions(merged)
    questions = post_process(questions)

    ok, missing, no_ans = validate(questions)

    if ok:
        print("\nALL 600 QUESTIONS PARSED SUCCESSFULLY!")
    else:
        if missing:
            print(f"\nMissing {len(missing)} questions — likely parsing edge cases.")
        if no_ans:
            print(f"\n{len(no_ans)} questions without answer — check underlines.")

    save_all(questions, OUTPUT_DIR)
    print("\nDONE!")
