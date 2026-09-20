#!/usr/bin/env python3
"""DevPilot content reference validator.

Spec: docs/19-content-spec.md §4 (ContentValidator rules CV-01 ...).
This script is the pre-implementation reference checker. The Java
`ContentValidator` must implement the same rule IDs with the same results.

Requirements: Python 3.9+, PyYAML (pip install pyyaml). No other dependencies.

Usage:
    python validate_content.py [CONTENT_DIR] [--report] [--placement-vectors]

    CONTENT_DIR          default: parent directory of this script's directory
    --report             print inventory and budget sanity tables
    --placement-vectors  print plan template placement test vectors (§5.4)

Exit code: 0 = no ERROR (WARN allowed), 1 = at least one ERROR, 2 = usage/IO error.
"""
from __future__ import annotations

import datetime as dt
import os
import re
import sys
from collections import Counter, defaultdict
from urllib.parse import urlparse

try:
    import yaml
except ImportError:  # pragma: no cover
    print("PyYAML is required: pip install pyyaml", file=sys.stderr)
    sys.exit(2)

# ---------------------------------------------------------------------------
# Enums (docs/04-domain-model-and-db.md §3)
# ---------------------------------------------------------------------------
SKILL_CATEGORIES = [
    "JAVA", "SPRING", "DATABASE", "WEB_HTTP", "NETWORK", "CS", "ALGORITHM",
    "TESTING", "DEVOPS", "SECURITY", "INTEGRATION", "PRACTICAL_ENGINEERING", "SYSTEM_DESIGN",
    "EXPLANATION",
]
TARGET_ROLES = {"JAVA_BACKEND", "INTEGRATION_ENGINEER"}
PRIORITIES = ["MUST", "SHOULD", "LATER"]
REVIEW_TYPES = {"RECALL", "BUG_SPOT", "EXPLAIN", "CHOICE"}
CHALLENGE_PURPOSES = {"PRACTICE", "DIAGNOSTIC"}
RUBRIC_AXES = {"IMPLEMENTATION", "EXPLANATION", "DEBUGGING"}
HINT_KEYS = ["QUESTION_ONLY", "CONCEPT_HINT", "DIRECTION"]
PHASES = ["PREPARATION", "CONSOLIDATION"]
AXES = ["knowledge", "implementation", "explanation", "debugging"]

# docs/03-system-architecture.md §9 devpilot.ai.trusted-source-hosts
TRUSTED_SOURCE_HOSTS = [
    "docs.spring.io", "spring.io", "docs.oracle.com", "openjdk.org", "www.postgresql.org",
    "owasp.org", "cheatsheetseries.owasp.org", "www.kisa.or.kr", "supabase.com", "dart.dev",
    "docs.flutter.dev", "api.flutter.dev", "pmd.github.io", "spotbugs.readthedocs.io",
    "checkstyle.org", "junit.org", "hibernate.org", "docs.jboss.org",
    "developer.mozilla.org", "www.rfc-editor.org", "git-scm.com",
]

# docs/06-learning-engine-rules.md §4.2
AXIS_COST_BP = {"knowledge": 5000, "implementation": 10000, "explanation": 4000, "debugging": 8000}
REVIEW_OVERHEAD_BP = 11500

# ---------------------------------------------------------------------------
# Patterns (docs/19-content-spec.md §4)
# ---------------------------------------------------------------------------
SKILL_CODE_RE = re.compile(r"^[A-Z][A-Z0-9_]*(\.[A-Z][A-Z0-9_]*)*$")
CONCEPT_KEY_RE = re.compile(r"^[A-Z0-9_.:-]{3,150}$")
SEED_KEY_RE = re.compile(
    r"^(PRACTICE|DIAGNOSTIC)\.[A-Z][A-Z0-9_]*(\.[A-Z][A-Z0-9_]*)*\.L([1-5])\.([0-9]{3})$")
TEMPLATE_KEY_RE = re.compile(r"^[A-Z][A-Z0-9_]{2,59}$")
MILESTONE_KEY_RE = re.compile(r"^[A-Z][A-Z0-9_]{2,59}$")
CURATED_ID_RE = re.compile(r"^CS-[A-Z0-9]+(-[A-Z0-9]+)*$")
CURATED_REPO_KEY_RE = re.compile(r"^[a-z][a-z0-9-]{1,29}$")
READING_KEY_RE = re.compile(r"^READ\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*\.[0-9]{3}$")
CONCEPT_READING_KEY_RE = re.compile(r"^DOC\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*\.[0-9]{3}$")
# docs/19-content-spec.md §3.1: files.conceptReadings is optional and defaults to this path.
# The catalog entry lands in P3 together with ConceptReadingRegistry (§3.13).
DEFAULT_CONCEPT_READINGS = "concept-readings.yaml"
COMMIT_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
CHOICE_OPTION_RE = re.compile(r"^\s*([A-E])\)\s+\S", re.MULTILINE)
# review_item concept keys generated at runtime (06 §8.3, coach findings)
RESERVED_CONCEPT_PREFIXES = ("CHALLENGE:", "COACH:")
DIAGNOSTIC_MIN_IMPORTANCE = 0.70

# Seed hint code detector: stricter than the AI CodeLeakGuard (threshold 0 lines).
CODE_LINE_PATTERNS = [
    re.compile(r";\s*$"),                                   # statement terminator
    re.compile(r"\{\s*$"),                                  # block open at EOL
    re.compile(r"^\s*\}"),                                  # block close
    re.compile(r"\b[A-Za-z_]\w*\s*\([^()]*\)\s*(;|\{|->)"),  # call/decl followed by ; { ->
    re.compile(r"\b[A-Za-z_]\w*\.[A-Za-z_]\w*\([^()]*\)"),   # qualified call a.b(...)
    re.compile(r"@[A-Za-z_]\w*\("),                          # annotation with arguments
    re.compile(r"=\s*new\s+[A-Z]\w*"),                       # assignment with new
    re.compile(r"\b(public|private|protected)\s+(static\s+)?[\w<>\[\]]+\s+\w+\s*[(=;]"),
    re.compile(r"\b(SELECT|UPDATE|INSERT|DELETE)\b.*\b(FROM|SET|INTO|WHERE)\b"),
]


class Result:
    def __init__(self) -> None:
        self.errors: list[tuple[str, str, str]] = []
        self.warns: list[tuple[str, str, str]] = []

    def error(self, rule: str, where: str, msg: str) -> None:
        self.errors.append((rule, where, msg))

    def warn(self, rule: str, where: str, msg: str) -> None:
        self.warns.append((rule, where, msg))


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
def load_yaml(path: str, res: Result, where: str):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return yaml.safe_load(f)
    except FileNotFoundError:
        res.error("CV-02", where, f"file not found: {path}")
    except yaml.YAMLError as e:
        res.error("CV-02", where, f"YAML parse error: {e}")
    return None


def check_keys(obj, allowed: set, required: set, res: Result, where: str) -> bool:
    if not isinstance(obj, dict):
        res.error("CV-03", where, f"expected mapping, got {type(obj).__name__}")
        return False
    ok = True
    for k in obj:
        if k not in allowed:
            res.error("CV-03", where, f"unknown field '{k}'")
            ok = False
    for k in required:
        if k not in obj or obj[k] is None:
            res.error("CV-03", where, f"missing required field '{k}'")
            ok = False
    return ok


def is_int(v) -> bool:
    return isinstance(v, int) and not isinstance(v, bool)


def str_len_ok(v, lo: int, hi: int) -> bool:
    return isinstance(v, str) and lo <= len(v.strip()) and len(v) <= hi


def floor_div(a: int, b: int) -> int:
    return a // b


def ceil_div(a: int, b: int) -> int:
    return -((-a) // b)


def code_lines(text: str) -> list[str]:
    hits = []
    for line in text.splitlines():
        if any(p.search(line) for p in CODE_LINE_PATTERNS):
            hits.append(line.strip())
    return hits


def host_allowed(host: str) -> bool:
    host = (host or "").lower()
    return any(host == h or host.endswith("." + h) for h in TRUSTED_SOURCE_HOSTS)


# ---------------------------------------------------------------------------
# Plan template placement (docs/19-content-spec.md §5)
# ---------------------------------------------------------------------------
def allocate(window_start: dt.date, window_end: dt.date, weights: list[int], min_days: int):
    """Return [(start, end)] for milestones placed in [window_start, window_end] (inclusive)."""
    n = len(weights)
    d = (window_end - window_start).days + 1
    w_total = sum(weights)
    out = []
    if d >= n * min_days:  # SEQUENTIAL
        extra = d - n * min_days
        base = [floor_div(extra * w, w_total) for w in weights]
        rem = [extra * w - b * w_total for w, b in zip(weights, base)]
        left = extra - sum(base)
        order = sorted(range(n), key=lambda i: (-rem[i], i))
        bonus = [0] * n
        for i in order[:left]:
            bonus[i] = 1
        cur = window_start
        for i in range(n):
            length = min_days + base[i] + bonus[i]
            end = cur + dt.timedelta(days=length - 1)
            out.append((cur, end))
            cur = end + dt.timedelta(days=1)
    else:  # COMPRESSED
        span = min(min_days, d)
        slack = d - span
        c_last = w_total - weights[-1]
        cum = 0
        for i in range(n):
            if n == 1 or slack == 0:
                off = 0
            else:
                off = floor_div(cum * slack, c_last)
            start = window_start + dt.timedelta(days=off)
            out.append((start, start + dt.timedelta(days=span - 1)))
            cum += weights[i]
    return out


def place_template(template: dict, today: dt.date, target: dt.date):
    """Return list of (key, phase, start, end). One window [today, target] in template order.
    Precondition: today <= target."""
    ms = template["milestones"]
    min_days = template.get("placement", {}).get("minMilestoneDays", 7)
    spans = allocate(today, target, [m["weightBp"] for m in ms], min_days)
    return [(m["key"], m["phase"], s, e) for m, (s, e) in zip(ms, spans)]


# ---------------------------------------------------------------------------
# validation
# ---------------------------------------------------------------------------
def validate(content_dir: str):
    res = Result()
    data = {"skills": [], "targets": [], "templates": [], "cards": [], "challenges": [],
            "sources": [], "repos": [], "readings": [], "conceptReadings": [], "catalog": None}

    # ---- CV-01 catalog -----------------------------------------------------
    catalog = load_yaml(os.path.join(content_dir, "catalog.yaml"), res, "catalog.yaml")
    if catalog is None:
        res.error("CV-01", "catalog.yaml", "catalog.yaml missing or unreadable")
        return res, data
    data["catalog"] = catalog
    check_keys(catalog, {"catalogVersion", "files", "diagnosticCategories", "retired"},
               {"catalogVersion", "files", "diagnosticCategories", "retired"}, res, "catalog.yaml")
    if not (is_int(catalog.get("catalogVersion")) and catalog["catalogVersion"] >= 1):
        res.error("CV-01", "catalog.yaml", "catalogVersion must be an integer >= 1")
    files = catalog.get("files") or {}
    check_keys(files, {"skillTrees", "roleTargets", "planTemplates", "reviewCards", "challenges",
                       "curatedSources", "curatedRepos", "conceptReadings"},
               {"skillTrees", "roleTargets", "planTemplates", "reviewCards", "challenges",
                "curatedSources", "curatedRepos"}, res, "catalog.yaml#files")
    retired = catalog.get("retired") or {}
    check_keys(retired, {"skillCodes", "challengeSeedKeys", "conceptKeys", "curatedSourceIds",
                         "readingKeys"},
               {"skillCodes", "challengeSeedKeys", "conceptKeys", "curatedSourceIds",
                "readingKeys"}, res, "catalog.yaml#retired")
    retired_skills = set(retired.get("skillCodes") or [])
    retired_seed = set(retired.get("challengeSeedKeys") or [])
    retired_concepts = set(retired.get("conceptKeys") or [])
    retired_sources = set(retired.get("curatedSourceIds") or [])
    retired_readings = set(retired.get("readingKeys") or [])
    diag_categories = catalog.get("diagnosticCategories") or []
    for c in diag_categories:
        if c not in SKILL_CATEGORIES:
            res.error("CV-01", "catalog.yaml#diagnosticCategories", f"unknown category {c}")

    listed = []
    for key in ("skillTrees", "roleTargets", "planTemplates", "reviewCards", "challenges"):
        v = files.get(key) or []
        if not isinstance(v, list) or not v:
            res.error("CV-02", f"catalog.yaml#files.{key}", "must be a non-empty list")
            continue
        listed.extend(v)
    for single in ("curatedSources", "curatedRepos"):
        if isinstance(files.get(single), str):
            listed.append(files[single])
    # files.conceptReadings is optional and falls back to the default path (docs/19 §3.1, §3.13)
    concept_readings_rel = files.get("conceptReadings")
    if not isinstance(concept_readings_rel, str):
        concept_readings_rel = DEFAULT_CONCEPT_READINGS
    listed.append(concept_readings_rel)
    if len(set(listed)) != len(listed):
        res.error("CV-02", "catalog.yaml#files", "duplicate file entry")

    # CV-04: every yaml under content subdirectories is listed
    listed_norm = {os.path.normpath(p) for p in listed}
    for root, dirs, fnames in os.walk(content_dir):
        dirs[:] = [d for d in dirs if d not in ("tools",) and not d.startswith(".")]
        for fn in fnames:
            if fn.endswith((".yaml", ".yml")):
                rel = os.path.normpath(os.path.relpath(os.path.join(root, fn), content_dir))
                if rel == "catalog.yaml":
                    continue
                if rel not in listed_norm:
                    res.error("CV-04", rel.replace(os.sep, "/"), "YAML file not listed in catalog.yaml")

    # ---- skill tree (CV-10..CV-19) ----------------------------------------
    skills_by_code: dict[str, dict] = {}
    for rel in files.get("skillTrees") or []:
        doc = load_yaml(os.path.join(content_dir, rel), res, rel)
        if doc is None:
            continue
        if not check_keys(doc, {"skills"}, {"skills"}, res, rel):
            continue
        for idx, s in enumerate(doc["skills"] or []):
            where = f"{rel}#skills[{idx}]"
            if not check_keys(s, {"code", "name", "category", "parent", "description",
                                  "whyItMatters", "minutesPerLevelStep", "prerequisites"},
                              {"code", "name", "category", "description"}, res, where):
                continue
            code = s["code"]
            where = f"{rel}#{code}"
            if not (isinstance(code, str) and SKILL_CODE_RE.match(code) and len(code) <= 100):
                res.error("CV-10", where, "code pattern/length invalid")
                continue
            if len(code.split(".")) > 3:
                res.error("CV-10", where, "depth > 3 segments")
            if code in skills_by_code:
                res.error("CV-11", where, "duplicate skill code")
                continue
            if code in retired_skills:
                res.error("CV-11", where, "code is in retired.skillCodes")
            s["_file"] = rel
            s["_order"] = len(skills_by_code)
            skills_by_code[code] = s
            data["skills"].append(s)

    roots = {}
    for code, s in skills_by_code.items():
        where = f"{s['_file']}#{code}"
        if s["category"] not in SKILL_CATEGORIES:
            res.error("CV-12", where, f"unknown category {s['category']}")
        parent = s.get("parent")
        if parent is None:
            if code != s["category"]:
                res.error("CV-12", where, "root skill code must equal category")
            else:
                roots[code] = s
            if s.get("prerequisites"):
                res.error("CV-19", where, "root skill must not have prerequisites")
        else:
            p = skills_by_code.get(parent)
            if p is None:
                res.error("CV-13", where, f"parent {parent} not found")
            else:
                if p["category"] != s["category"]:
                    res.error("CV-13", where, "parent category differs")
                if code.rsplit(".", 1)[0] != parent:
                    res.error("CV-13", where, "code must be parent.code + '.' + SEGMENT")
            if "minutesPerLevelStep" not in s:
                res.error("CV-15", where, "non-root skill requires minutesPerLevelStep")
            if "prerequisites" not in s:
                res.error("CV-16", where, "non-root skill requires prerequisites (may be [])")
        if not str_len_ok(s["name"], 1, 200):
            res.error("CV-14", where, "name length 1..200")
        if not str_len_ok(s["description"], 10, 300):
            res.error("CV-14", where, "description length 10..300")
        # CV-88: whyItMatters is optional here (CV-89 coverage for MUST skills is not
        # implemented yet — the seed skills predate the field), but when present it must be
        # a 20..200 char line and root skills must not have one.
        if "whyItMatters" in s:
            if parent is None:
                res.error("CV-88", where, "root skill must not have whyItMatters")
            elif not str_len_ok(s["whyItMatters"], 20, 200):
                res.error("CV-88", where, "whyItMatters length 20..200")
        mpls = s.get("minutesPerLevelStep", 120)
        # non-root 60 (docs/19 §7.5 "60 미만은 쓰지 않는다", O-11), root 10
        min_step = 60 if parent is not None else 10
        if not (is_int(mpls) and min_step <= mpls <= 2000):
            res.error("CV-15", where, f"minutesPerLevelStep must be integer {min_step}..2000")
        prereqs = s.get("prerequisites") or []
        if not isinstance(prereqs, list):
            res.error("CV-16", where, "prerequisites must be a list")
            prereqs = []
        if len(set(prereqs)) != len(prereqs):
            res.error("CV-16", where, "duplicate prerequisite")
        for pr in prereqs:
            if pr == code:
                res.error("CV-16", where, "self prerequisite")
            elif pr not in skills_by_code:
                res.error("CV-16", where, f"prerequisite {pr} not found")
            elif skills_by_code[pr].get("parent") is None:
                res.error("CV-19", where, f"prerequisite {pr} is a root skill")
    for cat in SKILL_CATEGORIES:
        if cat not in roots:
            res.error("CV-12", "skillTrees", f"missing root skill for category {cat}")

    # CV-17 cycle detection (iterative DFS, deterministic order)
    color = {c: 0 for c in skills_by_code}
    for start in sorted(skills_by_code):
        if color[start]:
            continue
        stack = [(start, iter(sorted(skills_by_code[start].get("prerequisites") or [])))]
        path = [start]
        color[start] = 1
        while stack:
            node, it = stack[-1]
            nxt = next(it, None)
            if nxt is None:
                color[node] = 2
                stack.pop()
                path.pop()
                continue
            if nxt not in color:
                continue
            if color[nxt] == 1:
                cyc = path[path.index(nxt):] + [nxt]
                res.error("CV-17", "skillTrees", "prerequisite cycle: " + " -> ".join(cyc))
            elif color[nxt] == 0:
                color[nxt] = 1
                path.append(nxt)
                stack.append((nxt, iter(sorted(skills_by_code[nxt].get("prerequisites") or []))))

    non_root = {c for c, s in skills_by_code.items() if s.get("parent") is not None}

    # ---- role targets (CV-20..CV-24) --------------------------------------
    targets_by_role: dict[str, dict[str, dict]] = defaultdict(dict)
    for rel in files.get("roleTargets") or []:
        doc = load_yaml(os.path.join(content_dir, rel), res, rel)
        if doc is None or not check_keys(doc, {"targetRole", "targets"}, {"targetRole", "targets"},
                                         res, rel):
            continue
        role = doc["targetRole"]
        if role not in TARGET_ROLES:
            res.error("CV-20", rel, f"unknown targetRole {role}")
            continue
        for idx, t in enumerate(doc["targets"] or []):
            where = f"{rel}#targets[{idx}]"
            if not check_keys(t, {"skill", "priority", "importance", "target"},
                              {"skill", "priority", "importance", "target"}, res, where):
                continue
            sk = t["skill"]
            where = f"{rel}#{sk}"
            if sk not in skills_by_code:
                res.error("CV-20", where, "skill not found")
                continue
            if sk not in non_root:
                res.error("CV-20", where, "root skill cannot have a role target")
                continue
            if sk in targets_by_role[role]:
                res.error("CV-21", where, "duplicate role target")
                continue
            t["_role"] = role
            targets_by_role[role][sk] = t
            data["targets"].append(t)
            if t["priority"] not in PRIORITIES:
                res.error("CV-22", where, f"unknown priority {t['priority']}")
            imp = t["importance"]
            if isinstance(imp, bool) or not isinstance(imp, (int, float)) or not (0 <= imp <= 1) \
                    or abs(imp * 100 - round(imp * 100)) > 1e-9:
                res.error("CV-22", where, "importance must be 0.00..1.00 with <= 2 decimals")
            tg = t["target"]
            if check_keys(tg, set(AXES), set(AXES), res, where + ".target"):
                if not all(is_int(tg[a]) and 0 <= tg[a] <= 5 for a in AXES):
                    res.error("CV-23", where, "target levels must be integers 0..5")
                elif sum(tg[a] for a in AXES) == 0:
                    res.error("CV-23", where, "at least one axis target must be > 0")
            cat = skills_by_code[sk]["category"]
            if cat == "ALGORITHM" and t["priority"] != "SHOULD":
                res.warn("CV-24", where, "DEC-14 default: ALGORITHM priority is SHOULD")
            if cat == "EXPLANATION" and t["priority"] != "MUST":
                res.warn("CV-24", where, "DEC-14 default: EXPLANATION priority is MUST")
    for role in TARGET_ROLES:
        missing = sorted(non_root - set(targets_by_role[role]))
        for sk in missing:
            res.error("CV-21", f"roleTargets[{role}]", f"missing role target for {sk}")
    tgt = targets_by_role["JAVA_BACKEND"]

    # CV-18 prerequisite readiness reachability (06 §5.2, §5.4)
    for code, s in skills_by_code.items():
        for pr in s.get("prerequisites") or []:
            t = tgt.get(pr)
            if t and isinstance(t.get("target"), dict) and is_int(t["target"].get("implementation")) \
                    and t["target"]["implementation"] < 2:
                res.error("CV-18", f"{s['_file']}#{code}",
                          f"prerequisite {pr} has target implementation < 2 (readiness unreachable)")

    # ---- plan templates (CV-30..CV-37) ------------------------------------
    template_roles = Counter()
    for rel in files.get("planTemplates") or []:
        doc = load_yaml(os.path.join(content_dir, rel), res, rel)
        if doc is None or not check_keys(
                doc, {"templateKey", "targetRole", "planTitle", "placement", "milestones"},
                {"templateKey", "targetRole", "planTitle", "placement", "milestones"}, res, rel):
            continue
        data["templates"].append(doc)
        if not (isinstance(doc["templateKey"], str) and TEMPLATE_KEY_RE.match(doc["templateKey"])):
            res.error("CV-30", rel, "templateKey pattern invalid")
        if doc["targetRole"] not in TARGET_ROLES:
            res.error("CV-30", rel, "unknown targetRole")
        template_roles[doc["targetRole"]] += 1
        if not str_len_ok(doc["planTitle"], 1, 200):
            res.error("CV-30", rel, "planTitle length 1..200")
        pl = doc["placement"]
        if check_keys(pl, {"minMilestoneDays"}, {"minMilestoneDays"}, res, rel + "#placement"):
            if not (is_int(pl["minMilestoneDays"]) and 1 <= pl["minMilestoneDays"] <= 28):
                res.error("CV-37", rel, "minMilestoneDays must be integer 1..28")
        ms = doc["milestones"] or []
        if not (1 <= len(ms) <= 24):
            res.error("CV-31", rel, "milestones count 1..24")
        keys = set()
        skill_seen: dict[str, str] = {}
        phases_seen = []
        weight_sum = 0
        for idx, m in enumerate(ms):
            where = f"{rel}#milestones[{idx}]"
            if not check_keys(m, {"key", "title", "description", "priority", "weightBp", "phase",
                                  "skillCodes"},
                              {"key", "title", "priority", "weightBp", "phase", "skillCodes"},
                              res, where):
                continue
            where = f"{rel}#{m['key']}"
            if not (isinstance(m["key"], str) and MILESTONE_KEY_RE.match(m["key"])):
                res.error("CV-31", where, "milestone key pattern invalid")
            if m["key"] in keys:
                res.error("CV-31", where, "duplicate milestone key")
            keys.add(m["key"])
            if not str_len_ok(m["title"], 1, 200):
                res.error("CV-31", where, "title length 1..200")
            if m.get("description") is not None and not str_len_ok(m["description"], 1, 2000):
                res.error("CV-31", where, "description length 1..2000")
            if not (is_int(m["weightBp"]) and 100 <= m["weightBp"] <= 10000):
                res.error("CV-32", where, "weightBp must be integer 100..10000")
            else:
                weight_sum += m["weightBp"]
            if m["phase"] not in PHASES:
                res.error("CV-33", where, "unknown phase")
            phases_seen.append(m["phase"])
            if m["priority"] not in PRIORITIES:
                res.error("CV-34", where, "unknown priority")
            codes = m["skillCodes"] or []
            if not (1 <= len(codes) <= 24):
                res.error("CV-35", where, "skillCodes count 1..24")
            if len(set(codes)) != len(codes):
                res.error("CV-35", where, "duplicate skill code in milestone")
            for sc in codes:
                if sc not in tgt:
                    res.error("CV-35", where, f"skill {sc} not found / root / no role target")
                if sc in skill_seen and skill_seen[sc] != m["key"]:
                    res.error("CV-35", where, f"skill {sc} already in milestone {skill_seen[sc]}")
                skill_seen.setdefault(sc, m["key"])
        if weight_sum != 10000:
            res.error("CV-32", rel, f"sum of weightBp must be 10000 (got {weight_sum})")
        if phases_seen and phases_seen != sorted(phases_seen, key=PHASES.index):
            res.error("CV-33", rel, "all PREPARATION milestones must precede CONSOLIDATION")
        for ph in PHASES:
            if ph not in phases_seen:
                res.error("CV-33", rel, f"phase {ph} needs at least one milestone")
        # CV-36 is per track: every MUST skill of THIS template's role is in a milestone
        for sk, t in sorted(targets_by_role.get(doc["targetRole"], {}).items()):
            if t["priority"] == "MUST" and sk not in skill_seen:
                res.error("CV-36", rel, f"MUST skill {sk} is not in any milestone")
        doc["_skill_milestone"] = skill_seen
    for role in TARGET_ROLES:
        if template_roles[role] != 1:
            res.error("CV-30", "planTemplates", f"exactly one template required for {role}")

    # ---- review cards (CV-40..CV-47) --------------------------------------
    concept_keys = set()
    for rel in files.get("reviewCards") or []:
        doc = load_yaml(os.path.join(content_dir, rel), res, rel)
        if doc is None or not check_keys(doc, {"cards"}, {"cards"}, res, rel):
            continue
        for idx, c in enumerate(doc["cards"] or []):
            where = f"{rel}#cards[{idx}]"
            if not check_keys(c, {"conceptKey", "skill", "reviewType", "prompt", "expectedAnswer",
                                  "rubric"},
                              {"conceptKey", "skill", "reviewType", "prompt", "expectedAnswer",
                               "rubric"}, res, where):
                continue
            ck = c["conceptKey"]
            where = f"{rel}#{ck}"
            if not (isinstance(ck, str) and CONCEPT_KEY_RE.match(ck)):
                res.error("CV-40", where, "conceptKey pattern invalid")
            if isinstance(ck, str) and ck.startswith(RESERVED_CONCEPT_PREFIXES):
                res.error("CV-40", where, "conceptKey prefixes CHALLENGE: and COACH: are reserved")
            if ck in concept_keys:
                res.error("CV-40", where, "duplicate conceptKey")
            if ck in retired_concepts:
                res.error("CV-40", where, "conceptKey is retired")
            concept_keys.add(ck)
            c["_file"] = rel
            data["cards"].append(c)
            sk = c["skill"]
            if sk not in tgt:
                res.error("CV-41", where, f"skill {sk} not found / root / no role target")
            elif not (isinstance(ck, str) and ck.startswith(sk + ".")):
                res.error("CV-41", where, "conceptKey must start with skill code + '.'")
            rt = c["reviewType"]
            if rt not in REVIEW_TYPES:
                res.error("CV-42", where, f"unknown reviewType {rt}")
            if not str_len_ok(c["prompt"], 10, 1200):
                res.error("CV-43", where, "prompt length 10..1200")
            if not str_len_ok(c["expectedAnswer"], 10, 1500):
                res.error("CV-43", where, "expectedAnswer length 10..1500")
            rub = c["rubric"] or []
            if not (2 <= len(rub) <= 4):
                res.error("CV-44", where, "rubric must have 2..4 items")
            for i, r in enumerate(rub):
                if not check_keys(r, {"id", "criterion"}, {"id", "criterion"}, res,
                                  f"{where}.rubric[{i}]"):
                    continue
                if r["id"] != f"R{i + 1}":
                    res.error("CV-44", where, f"rubric ids must be R1..Rn in order (got {r['id']})")
                if not str_len_ok(r["criterion"], 5, 200):
                    res.error("CV-44", where, "criterion length 5..200")
            if rt == "BUG_SPOT" and "```" not in (c["prompt"] or ""):
                res.error("CV-45", where, "BUG_SPOT prompt must contain a fenced code block")
            if rt == "BUG_SPOT" and isinstance(c["prompt"], str):
                m = re.search(r"```[a-z]*\n(.*?)```", c["prompt"], re.S)
                if m and len(m.group(1).strip().splitlines()) > 15:
                    res.error("CV-45", where, "BUG_SPOT code block must be <= 15 lines")
            if rt == "CHOICE":
                labels = CHOICE_OPTION_RE.findall(c["prompt"] or "")
                if not (3 <= len(labels) <= 5) or labels != [chr(65 + i) for i in range(len(labels))]:
                    res.error("CV-46", where, "CHOICE prompt needs 3..5 options labeled A) B) C) ...")
                ans = (c["expectedAnswer"] or "").strip()
                if not re.match(r"^[A-E]\b", ans) or ans[0] not in labels:
                    res.error("CV-46", where, "CHOICE expectedAnswer must start with the correct label")
            if rt != "BUG_SPOT" and "```" in (c["prompt"] or ""):
                res.warn("CV-49", where, "code block in non-BUG_SPOT card prompt")
            if rub and isinstance(rub[0], dict) and isinstance(rub[0].get("criterion"), str) \
                    and rub[0]["criterion"].strip() == str(c["expectedAnswer"]).strip():
                res.error("CV-47", where, "R1 criterion must not equal expectedAnswer (R1 is the hint)")

    # ---- challenges (CV-50..CV-60) ----------------------------------------
    seed_keys = set()
    for rel in files.get("challenges") or []:
        doc = load_yaml(os.path.join(content_dir, rel), res, rel)
        if doc is None or not check_keys(doc, {"challenges"}, {"challenges"}, res, rel):
            continue
        for idx, ch in enumerate(doc["challenges"] or []):
            where = f"{rel}#challenges[{idx}]"
            allowed = {"seedKey", "purpose", "skills", "difficulty", "estimatedMinutes",
                       "isTransfer", "title", "scenario", "prompt", "constraints",
                       "expectedConcepts", "rubric", "commonMistakes", "transferTargets", "hints"}
            if not check_keys(ch, allowed, allowed - {"constraints"}, res, where):
                continue
            sk_ = ch["seedKey"]
            where = f"{rel}#{sk_}"
            m = SEED_KEY_RE.match(sk_) if isinstance(sk_, str) else None
            if not m or len(sk_) > 100:
                res.error("CV-50", where, "seedKey pattern/length invalid")
            else:
                if m.group(1) != ch["purpose"]:
                    res.error("CV-50", where, "seedKey prefix must equal purpose")
                if int(m.group(3)) != ch["difficulty"]:
                    res.error("CV-50", where, "seedKey L{n} must equal difficulty")
            if sk_ in seed_keys:
                res.error("CV-50", where, "duplicate seedKey")
            if sk_ in retired_seed:
                res.error("CV-50", where, "seedKey is retired")
            seed_keys.add(sk_)
            ch["_file"] = rel
            data["challenges"].append(ch)
            if ch["purpose"] not in CHALLENGE_PURPOSES:
                res.error("CV-51", where, "unknown purpose")
            if not (is_int(ch["difficulty"]) and 1 <= ch["difficulty"] <= 5):
                res.error("CV-51", where, "difficulty must be 1..5")
            if not (is_int(ch["estimatedMinutes"]) and 5 <= ch["estimatedMinutes"] <= 180):
                res.error("CV-51", where, "estimatedMinutes must be 5..180")
            if not isinstance(ch["isTransfer"], bool):
                res.error("CV-51", where, "isTransfer must be boolean")
            skills = ch["skills"] or []
            if not (1 <= len(skills) <= 3) or len(set(skills)) != len(skills):
                res.error("CV-52", where, "skills must be 1..3 distinct codes")
            for s in skills:
                if s not in tgt:
                    res.error("CV-52", where, f"skill {s} not found / root / no role target")
            if not str_len_ok(ch["title"], 1, 200):
                res.error("CV-53", where, "title length 1..200")
            if not str_len_ok(ch["scenario"], 20, 3000):
                res.error("CV-53", where, "scenario length 20..3000")
            if not str_len_ok(ch["prompt"], 20, 3000):
                res.error("CV-53", where, "prompt length 20..3000")
            for fld, lo, hi in (("constraints", 0, 6), ("expectedConcepts", 2, 8),
                                ("commonMistakes", 1, 6), ("transferTargets", 0, 5)):
                v = ch.get(fld, [])
                if v is None:
                    v = []
                if not isinstance(v, list) or not (lo <= len(v) <= hi) or \
                        not all(isinstance(x, str) and 1 <= len(x) <= 300 for x in v):
                    res.error("CV-53", where, f"{fld} must be a list of {lo}..{hi} strings (1..300)")
            rub = ch["rubric"] or []
            if not (2 <= len(rub) <= 6):
                res.error("CV-54", where, "rubric must have 2..6 items")
            total = 0
            axes = set()
            for i, r in enumerate(rub):
                if not check_keys(r, {"id", "criterion", "weightBp", "axis"},
                                  {"id", "criterion", "weightBp", "axis"}, res, f"{where}.rubric[{i}]"):
                    continue
                if r["id"] != f"R{i + 1}":
                    res.error("CV-54", where, f"rubric ids must be R1..Rn in order (got {r['id']})")
                if not str_len_ok(r["criterion"], 5, 200):
                    res.error("CV-54", where, "criterion length 5..200")
                w = r["weightBp"]
                if not (is_int(w) and 500 <= w <= 6000 and w % 500 == 0):
                    res.error("CV-54", where, f"weightBp must be multiple of 500 in 500..6000 ({w})")
                else:
                    total += w
                if r["axis"] not in RUBRIC_AXES:
                    res.error("CV-54", where, f"unknown axis {r['axis']}")
                axes.add(r["axis"])
            if total != 10000:
                res.error("CV-54", where, f"sum of weightBp must be 10000 (got {total})")
            if "EXPLANATION" not in axes or not (axes & {"IMPLEMENTATION", "DEBUGGING"}):
                res.error("CV-54", where, "rubric needs >=1 EXPLANATION and >=1 IMPLEMENTATION/DEBUGGING item")
            hints = ch["hints"]
            if check_keys(hints, set(HINT_KEYS), set(HINT_KEYS), res, where + ".hints"):
                for hk in HINT_KEYS:
                    h = hints[hk]
                    if not str_len_ok(h, 10, 300):
                        res.error("CV-55", where, f"hint {hk} length 10..300")
                        continue
                    if "`" in h:
                        res.error("CV-56", where, f"hint {hk} contains backtick/code fence")
                    hits = code_lines(h)
                    if hits:
                        res.error("CV-56", where, f"hint {hk} contains code-like line: {hits[0]!r}")
                q = hints["QUESTION_ONLY"] if isinstance(hints["QUESTION_ONLY"], str) else ""
                if not q.rstrip().endswith("?"):
                    res.error("CV-57", where, "QUESTION_ONLY must end with '?'")
                for concept in ch["expectedConcepts"] or []:
                    if isinstance(concept, str) and concept.lower() in q.lower():
                        res.error("CV-57", where, f"QUESTION_ONLY reveals expected concept '{concept}'")
            for tt in ch.get("transferTargets") or []:
                if tt not in tgt:
                    res.error("CV-58", where, f"transfer target {tt} not found / root / no role target")
                if tt in skills:
                    res.error("CV-58", where, f"transfer target {tt} duplicates a challenge skill")
            if ch["isTransfer"] is True and is_int(ch["difficulty"]) and ch["difficulty"] < 3:
                res.error("CV-58", where, "isTransfer=true requires difficulty >= 3")
            if ch["purpose"] == "DIAGNOSTIC":
                if ch["difficulty"] != 3:
                    res.error("CV-59", where, "DIAGNOSTIC difficulty must be 3")
                if is_int(ch["estimatedMinutes"]) and ch["estimatedMinutes"] > 15:
                    res.error("CV-59", where, "DIAGNOSTIC estimatedMinutes must be <= 15")
                if ch["isTransfer"]:
                    res.error("CV-59", where, "DIAGNOSTIC isTransfer must be false")
                cats = {skills_by_code[s]["category"] for s in skills if s in skills_by_code}
                if len(cats) != 1:
                    res.error("CV-59", where, "DIAGNOSTIC skills must share one category")
                for s in skills:
                    t = tgt.get(s)
                    if t and (t.get("priority") != "MUST" or not isinstance(t.get("importance"), (int, float))
                              or t["importance"] < DIAGNOSTIC_MIN_IMPORTANCE - 1e-9):
                        res.error("CV-59", where,
                                  f"DIAGNOSTIC skill {s} must be MUST with importance >= 0.70")
            elif is_int(ch["difficulty"]) and is_int(ch["estimatedMinutes"]):
                limit = {1: 20, 2: 30, 3: 40, 4: 60, 5: 90}[ch["difficulty"]]
                if ch["estimatedMinutes"] > limit:
                    res.warn("CV-60", where, f"L{ch['difficulty']} estimatedMinutes > {limit}")
    for cat in diag_categories:
        found = [ch for ch in data["challenges"] if ch.get("purpose") == "DIAGNOSTIC" and
                 {skills_by_code[s]["category"] for s in ch["skills"] if s in skills_by_code} == {cat}]
        if not found:
            res.error("CV-59", "challenges", f"no DIAGNOSTIC challenge for category {cat}")

    # ---- curated sources (CV-70..CV-72) -----------------------------------
    rel = files.get("curatedSources")
    if isinstance(rel, str):
        doc = load_yaml(os.path.join(content_dir, rel), res, rel)
        if doc is not None and check_keys(doc, {"sources"}, {"sources"}, res, rel):
            ids = set()
            for idx, src in enumerate(doc["sources"] or []):
                where = f"{rel}#sources[{idx}]"
                fields = {"id", "title", "url", "publisher", "versionScope", "claim", "verifiedAt"}
                if not check_keys(src, fields, fields, res, where):
                    continue
                where = f"{rel}#{src['id']}"
                if not (isinstance(src["id"], str) and CURATED_ID_RE.match(src["id"])
                        and len(src["id"]) <= 80):
                    res.error("CV-70", where, "id pattern invalid")
                if src["id"] in ids:
                    res.error("CV-70", where, "duplicate id")
                if src["id"] in retired_sources:
                    res.error("CV-70", where, "id is retired")
                ids.add(src["id"])
                u = urlparse(str(src["url"]))
                if u.scheme != "https" or not host_allowed(u.hostname or ""):
                    res.error("CV-71", where, f"url must be https on a trusted host ({u.hostname})")
                for fld, hi in (("title", 200), ("publisher", 100), ("versionScope", 100),
                                ("claim", 300)):
                    if not str_len_ok(src[fld], 1, hi):
                        res.error("CV-72", where, f"{fld} length 1..{hi}")
                va = src["verifiedAt"]
                if not isinstance(va, dt.date):
                    try:
                        dt.date.fromisoformat(str(va))
                    except ValueError:
                        res.error("CV-72", where, "verifiedAt must be YYYY-MM-DD")
                data["sources"].append(src)

    # ---- curated repos / readings (CV-80..CV-87) --------------------------
    # reading keys live in ONE namespace: code readings (READ.*) in curated-repos.yaml and
    # concept readings (DOC.*) in concept-readings.yaml both land in learning_task.reading_key
    # and are served by GET /readings/{key} (docs/05 §19.7, docs/19 §3.13). Keys must not collide.
    reading_keys: set[str] = set()
    rel = files.get("curatedRepos")
    if isinstance(rel, str):
        doc = load_yaml(os.path.join(content_dir, rel), res, rel)
        if doc is not None and check_keys(doc, {"repos", "readings"}, {"repos", "readings"}, res, rel):
            repos = doc.get("repos") or []
            readings = doc.get("readings") or []
            if not isinstance(repos, list) or not repos:
                res.error("CV-80", rel, "repos must be a non-empty list")
                repos = []
            if not isinstance(readings, list) or not readings:
                res.error("CV-80", rel, "readings must be a non-empty list")
                readings = []

            repo_keys: set[str] = set()
            for idx, repo in enumerate(repos):
                where = f"{rel}#repos[{idx}]"
                allowed = {"key", "name", "url", "subPath", "pinnedCommit", "license", "stack",
                           "why", "cloneHint", "licenseNote"}
                # pinnedCommit may be absent or null when the SHA lookup failed (19 §8.4)
                required = {"key", "name", "url", "subPath", "license", "stack",
                            "why", "cloneHint"}
                if not check_keys(repo, allowed, required, res, where):
                    continue
                key = repo["key"]
                where = f"{rel}#{key}"
                if not (isinstance(key, str) and CURATED_REPO_KEY_RE.match(key)):
                    res.error("CV-81", where, "repo key pattern invalid (^[a-z][a-z0-9-]{1,29}$)")
                if key in repo_keys:
                    res.error("CV-81", where, "duplicate repo key")
                repo_keys.add(key)
                u = urlparse(str(repo["url"]))
                if u.scheme != "https" or not u.netloc:
                    res.error("CV-81", where, f"url must be https ({repo['url']})")
                sub = repo["subPath"]
                if not isinstance(sub, str) or sub.startswith("/") or ".." in sub.split("/"):
                    res.error("CV-81", where, "subPath must be a relative path without '..'")
                for fld, lo, hi in (("name", 1, 200), ("license", 1, 50), ("stack", 1, 200),
                                    ("why", 10, 500), ("cloneHint", 10, 500)):
                    if not str_len_ok(repo[fld], lo, hi):
                        res.error("CV-81", where, f"{fld} length {lo}..{hi}")
                pinned = repo.get("pinnedCommit")
                if pinned is not None and not (isinstance(pinned, str) and COMMIT_SHA_RE.match(pinned)):
                    res.error("CV-82", where, "pinnedCommit must be a 40-char lowercase hex SHA or null")
                if pinned is None:
                    res.warn("CV-82", where, "pinnedCommit is null: reading line numbers are unpinned")
                repo["_readings"] = 0  # readings that are not retired (CV-87)
                data["repos"].append(repo)

            repos_by_key = {r["key"]: r for r in data["repos"]}
            for idx, rd in enumerate(readings):
                where = f"{rel}#readings[{idx}]"
                required = {"key", "repo", "path", "lines", "skillCodes", "estimatedMinutes",
                            "question", "lookFor"}
                # optional: retired (bool, default false) — docs/19 §3.8, §8.2
                if not check_keys(rd, required | {"retired"}, required, res, where):
                    continue
                if "retired" in rd and not isinstance(rd["retired"], bool):
                    res.error("CV-03", where, "retired must be a boolean")
                is_retired = rd.get("retired") is True
                key = rd["key"]
                where = f"{rel}#{key}"
                if not (isinstance(key, str) and READING_KEY_RE.match(key) and len(key) <= 100):
                    res.error("CV-83", where, "reading key pattern invalid "
                                              r"(^READ\.<REPO>\.<TOPIC>\.NNN$)")
                if key in reading_keys:
                    res.error("CV-83", where, "duplicate reading key")
                # CV-83 retirement: an active reading must not use a retired key, and a
                # retired reading must be listed in retired.readingKeys (docs/19 §8.2)
                if is_retired and key not in retired_readings:
                    res.error("CV-83", where, "retired reading key must be listed in retired.readingKeys")
                if not is_retired and key in retired_readings:
                    res.error("CV-83", where, "key is in retired.readingKeys but the reading is not retired")
                reading_keys.add(key)

                if rd["repo"] not in repos_by_key:
                    res.error("CV-84", where, f"unknown repo reference {rd['repo']}")
                elif not is_retired:
                    repos_by_key[rd["repo"]]["_readings"] += 1

                path = rd["path"]
                if not (isinstance(path, str) and str_len_ok(path, 1, 300)):
                    res.error("CV-85", where, "path must be a string of length 1..300")
                elif path.startswith("/") or "\\" in path or ".." in path.split("/"):
                    res.error("CV-85", where,
                              "path must be a relative POSIX path without '..' segments")

                lines = rd["lines"]
                if not (isinstance(lines, list) and len(lines) == 2 and all(is_int(v) for v in lines)):
                    res.error("CV-85", where, "lines must be [start, end] integers")
                elif not (lines[0] >= 1 and lines[1] >= 1):
                    res.error("CV-85", where, "lines must be positive")
                elif lines[0] > lines[1]:
                    res.error("CV-85", where, f"lines must be ascending ({lines[0]} > {lines[1]})")

                codes = rd["skillCodes"]
                if not (isinstance(codes, list) and 1 <= len(codes) <= 4):
                    res.error("CV-86", where, "skillCodes must be a list of 1..4 codes")
                else:
                    if len(set(codes)) != len(codes):
                        res.error("CV-86", where, "duplicate skillCode")
                    for c in codes:
                        if c not in tgt:
                            res.error("CV-86", where, f"skillCode {c} has no role target")
                em = rd["estimatedMinutes"]
                if not (is_int(em) and 5 <= em <= 60):
                    res.error("CV-86", where, "estimatedMinutes must be an integer 5..60")
                q = rd["question"]
                if not str_len_ok(q, 40, 300):
                    res.error("CV-86", where,
                              f"question must be 40..300 chars (got {len(q) if isinstance(q, str) else 'n/a'})")
                lf = rd["lookFor"]
                if not (isinstance(lf, list) and 1 <= len(lf) <= 5):
                    res.error("CV-86", where, "lookFor must be a list of 1..5 items")
                else:
                    for item in lf:
                        if not str_len_ok(item, 5, 200):
                            res.error("CV-86", where, "lookFor item length 5..200")
                data["readings"].append(rd)

            # CV-87: count only readings that are not retired. A repo kept only for retired
            # readings (0 active) is not a warning target. Range is 3..8 (docs/19 §3.8, CV-87)
            for repo in data["repos"]:
                active = repo["_readings"]
                if active and not 3 <= active <= 8:
                    res.warn("CV-87", f"{rel}#{repo['key']}",
                             f"repo has {active} active readings (expected 3..8)")

    # ---- concept readings (CV-120..CV-125) --------------------------------
    crel = concept_readings_rel
    if os.path.isfile(os.path.join(content_dir, crel)):
        doc = load_yaml(os.path.join(content_dir, crel), res, crel)
        if doc is not None and check_keys(doc, {"conceptReadings"}, {"conceptReadings"}, res, crel):
            entries = doc.get("conceptReadings") or []
            if not isinstance(entries, list) or not entries:
                res.error("CV-120", crel, "conceptReadings must be a non-empty list")
                entries = []
            for idx, cr in enumerate(entries):
                where = f"{crel}#conceptReadings[{idx}]"
                required = {"key", "title", "url", "publisher", "versionScope", "skillCodes",
                            "estimatedMinutes", "whyRead", "checkPoints", "verifiedAt"}
                # optional: retired (bool, default false) — docs/19 §3.13, §8.2
                if not check_keys(cr, required | {"retired"}, required, res, where):
                    continue
                if "retired" in cr and not isinstance(cr["retired"], bool):
                    res.error("CV-03", where, "retired must be a boolean")
                is_retired = cr.get("retired") is True
                key = cr["key"]
                where = f"{crel}#{key}"
                if not (isinstance(key, str) and CONCEPT_READING_KEY_RE.match(key)
                        and len(key) <= 100):
                    res.error("CV-120", where, "concept reading key pattern invalid "
                                               r"(^DOC\.<TOPIC>\.<UNIT>\.NNN$)")
                if key in reading_keys:
                    res.error("CV-120", where, "duplicate reading key "
                                               "(code readings and concept readings share one namespace)")
                if is_retired and key not in retired_readings:
                    res.error("CV-120", where,
                              "retired concept reading key must be listed in retired.readingKeys")
                if not is_retired and key in retired_readings:
                    res.error("CV-120", where,
                              "key is in retired.readingKeys but the concept reading is not retired")
                reading_keys.add(key)

                u = urlparse(str(cr["url"]))
                if u.scheme != "https" or not host_allowed(u.hostname or ""):
                    res.error("CV-121", where,
                              f"url must be https on a trusted host ({u.hostname})")

                for fld, hi in (("title", 200), ("publisher", 100), ("versionScope", 100)):
                    if not str_len_ok(cr[fld], 1, hi):
                        res.error("CV-122", where, f"{fld} length 1..{hi}")
                va = cr["verifiedAt"]
                if not isinstance(va, dt.date):
                    try:
                        dt.date.fromisoformat(str(va))
                    except ValueError:
                        res.error("CV-122", where, "verifiedAt must be YYYY-MM-DD")

                codes = cr["skillCodes"]
                if not (isinstance(codes, list) and 1 <= len(codes) <= 4):
                    res.error("CV-123", where, "skillCodes must be a list of 1..4 codes")
                else:
                    if len(set(codes)) != len(codes):
                        res.error("CV-123", where, "duplicate skillCode")
                    for c in codes:
                        if c not in tgt:
                            res.error("CV-123", where, f"skillCode {c} has no role target")
                em = cr["estimatedMinutes"]
                if not (is_int(em) and 5 <= em <= 60):
                    res.error("CV-123", where, "estimatedMinutes must be an integer 5..60")

                if not str_len_ok(cr["whyRead"], 40, 400):
                    res.error("CV-124", where, "whyRead must be 40..400 chars")
                cps = cr["checkPoints"]
                if not (isinstance(cps, list) and len(cps) == 3):
                    res.error("CV-124", where,
                              "checkPoints must be exactly 3 items (06 §5.3 \"핵심 3가지\")")
                else:
                    for item in cps:
                        if not str_len_ok(item, 10, 200):
                            res.error("CV-124", where, "checkPoint length 10..200")
                    if len(set(cps)) != len(cps):
                        res.error("CV-124", where, "duplicate checkPoint")
                if not is_retired:
                    data["conceptReadings"].append(cr)

    # CV-83/CV-120: every retired key keeps its definition in one of the two reading files
    # (retired units stay resolvable through GET /readings/{key}, docs/19 §8.2)
    for key in sorted(retired_readings - reading_keys):
        res.error("CV-83", "catalog.yaml#retired.readingKeys",
                  f"{key} has no reading definition (keep it with retired: true)")

    # ---- CV-125 WARN: MUST skills with nothing to read --------------------
    readable = {c for rd in data["readings"] if not rd.get("retired")
                for c in (rd.get("skillCodes") or [])}
    readable |= {c for cr in data["conceptReadings"] for c in (cr.get("skillCodes") or [])}
    for sk, t in sorted(tgt.items()):
        if t["priority"] == "MUST" and sk not in readable:
            res.warn("CV-125", sk, "MUST skill has neither a code reading nor a concept reading")

    # ---- WARN: MUST skills without seed card ------------------------------
    carded = {c["skill"] for c in data["cards"]}
    for sk, t in sorted(tgt.items()):
        if t["priority"] == "MUST" and sk not in carded:
            res.warn("CV-48", sk, "MUST skill has no seed review card")

    # ---- CV-61 placement smoke test ----------------------------------------
    for tpl in data["templates"]:
        try:
            today = dt.date(2026, 10, 1)
            targets = [today, today + dt.timedelta(days=9),
                       today + dt.timedelta(days=30), today + dt.timedelta(days=210)]
            for target in targets:
                for key, phase, s, e in place_template(tpl, today, target):
                    if not (today <= s <= e <= target):
                        res.error("CV-61", tpl.get("templateKey", "?"),
                                  f"placement out of range for {key}: {s}..{e}")
        except (KeyError, TypeError, ZeroDivisionError) as ex:
            res.error("CV-61", tpl.get("templateKey", "?"), f"placement failed: {ex!r}")

    data["skills_by_code"] = skills_by_code
    data["tgt"] = tgt
    return res, data


# ---------------------------------------------------------------------------
# reports
# ---------------------------------------------------------------------------
def required_minutes(target: dict, planning: int, step: int) -> int:
    gap = sum(max(0, target[a] - planning) * AXIS_COST_BP[a] for a in AXES)
    return ceil_div(gap * step * REVIEW_OVERHEAD_BP, 100_000_000)


def risk_level(required_must: int, effective: int) -> str:
    if required_must == 0:
        return "LOW"
    if effective == 0:
        return "CRITICAL"
    if required_must * 10000 <= effective * 8000:
        return "LOW"
    if required_must * 10000 <= effective * 10000:
        return "MEDIUM"
    if required_must * 10000 <= effective * 12500:
        return "HIGH"
    return "CRITICAL"


def print_report(data: dict) -> None:
    sbc = data["skills_by_code"]
    tgt = data["tgt"]
    cat_of = lambda code: sbc[code]["category"] if code in sbc else "?"  # noqa: E731

    print("\n## Inventory")
    header = ("category", "skills", "MUST", "SHOULD", "LATER", "cards", "PRACTICE", "DIAGNOSTIC")
    print("| " + " | ".join(header) + " |")
    print("|" + "---|" * len(header))
    totals = Counter()
    for cat in SKILL_CATEGORIES:
        row = Counter()
        row["skills"] = sum(1 for s in sbc.values() if s["category"] == cat)
        for sk, t in tgt.items():
            if cat_of(sk) == cat:
                row[t["priority"]] += 1
        row["cards"] = sum(1 for c in data["cards"] if cat_of(c["skill"]) == cat)
        for ch in data["challenges"]:
            if ch["skills"] and cat_of(ch["skills"][0]) == cat:
                row[ch["purpose"]] += 1
        totals.update(row)
        print(f"| {cat} | {row['skills']} | {row['MUST']} | {row['SHOULD']} | {row['LATER']} | "
              f"{row['cards']} | {row['PRACTICE']} | {row['DIAGNOSTIC']} |")
    print(f"| **Total** | {totals['skills']} | {totals['MUST']} | {totals['SHOULD']} | "
          f"{totals['LATER']} | {totals['cards']} | {totals['PRACTICE']} | {totals['DIAGNOSTIC']} |")
    print(f"\nroot skills: {sum(1 for s in sbc.values() if s.get('parent') is None)}, "
          f"non-root: {sum(1 for s in sbc.values() if s.get('parent') is not None)}, "
          f"curated sources: {len(data['sources'])}")
    print("review types:", dict(sorted(Counter(c["reviewType"] for c in data["cards"]).items())))
    print("challenge difficulty (PRACTICE):",
          dict(sorted(Counter(ch["difficulty"] for ch in data["challenges"]
                              if ch["purpose"] == "PRACTICE").items())))

    print("\n## Budget sanity (06 §3, §4) — uniform planning level on all axes, weekday 45 / weekend 240,"
          " completion 7000bp, full weeks")
    weeks_list = (12, 26, 39)
    print("| planning | requiredMust | requiredShould | " +
          " | ".join(f"{w}w effective / risk" for w in weeks_list) + " |")
    print("|" + "---|" * (3 + len(weeks_list)))
    for p in range(0, 4):
        must = sum(required_minutes(t["target"], p, sbc[sk].get("minutesPerLevelStep", 120))
                   for sk, t in tgt.items() if t["priority"] == "MUST")
        should = sum(required_minutes(t["target"], p, sbc[sk].get("minutesPerLevelStep", 120))
                     for sk, t in tgt.items() if t["priority"] == "SHOULD")
        cells = []
        for w in weeks_list:
            nominal = w * (5 * 45 + 2 * 240)
            eff = floor_div(nominal * 7000, 10000)
            cells.append(f"{eff} / {risk_level(must, eff)}")
        print(f"| {p} | {must} | {should} | " + " | ".join(cells) + " |")


def print_placement_vectors(data: dict) -> None:
    if not data["templates"]:
        return
    tpl = data["templates"][0]
    d = dt.date
    cases = [
        ("V1 212 days", d(2026, 10, 1), d(2027, 4, 30)),
        ("V2 182 days", d(2026, 10, 1), d(2027, 3, 31)),
        ("V3 short 20 days", d(2026, 10, 1), d(2026, 10, 20)),
        ("V4 5 days", d(2026, 10, 1), d(2026, 10, 5)),
        ("V5 92 days", d(2026, 10, 1), d(2026, 12, 31)),
        ("V6 62 days, compressed", d(2026, 10, 1), d(2026, 12, 1)),
        ("V7 target == today", d(2026, 10, 1), d(2026, 10, 1)),
        ("V8 63 days = 9 x minDays, sequential", d(2026, 10, 1), d(2026, 12, 2)),
    ]
    for name, today, target in cases:
        print(f"\n### {name}: today={today} targetCompletionDate={target}")
        print("| key | phase | start | end | days |")
        print("|---|---|---|---|---|")
        for key, phase, s, e in place_template(tpl, today, target):
            print(f"| {key} | {phase} | {s} | {e} | {(e - s).days + 1} |")


def main(argv: list[str]) -> int:
    for stream in (sys.stdout, sys.stderr):  # Windows consoles default to a legacy code page
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    args = [a for a in argv if not a.startswith("--")]
    flags = {a for a in argv if a.startswith("--")}
    unknown = flags - {"--report", "--placement-vectors"}
    if unknown or len(args) > 1:
        print(__doc__, file=sys.stderr)
        return 2
    content_dir = args[0] if args else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if not os.path.isdir(content_dir):
        print(f"not a directory: {content_dir}", file=sys.stderr)
        return 2
    res, data = validate(content_dir)
    for rule, where, msg in sorted(res.errors):
        print(f"ERROR {rule} {where}: {msg}")
    for rule, where, msg in sorted(res.warns):
        print(f"WARN  {rule} {where}: {msg}")
    print(f"\nskills={len(data['skills'])} roleTargets={len(data['targets'])} "
          f"templates={len(data['templates'])} cards={len(data['cards'])} "
          f"challenges={len(data['challenges'])} curatedSources={len(data['sources'])} "
          f"curatedRepos={len(data['repos'])} readings={len(data['readings'])} "
          f"conceptReadings={len(data['conceptReadings'])} "
          f"catalogVersion={(data['catalog'] or {}).get('catalogVersion')}")
    print(f"result: {'FAIL' if res.errors else 'PASS'} (errors={len(res.errors)}, warnings={len(res.warns)})")
    if "--report" in flags and "skills_by_code" in data:
        print_report(data)
    if "--placement-vectors" in flags:
        print_placement_vectors(data)
    return 1 if res.errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
