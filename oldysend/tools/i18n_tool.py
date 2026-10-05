#!/usr/bin/env python3
"""Helpers for completing translations.

  python tools/i18n_tool.py missing <locale> [--oldy]     prints {"a/b/c": english value} of keys the locale lacks
  python tools/i18n_tool.py merge <locale> <file> [--oldy] merges {"a/b/c": value} into the locale file
  python tools/i18n_tool.py report                         missing-key counts of every locale

Paths use "/" between raw slang keys (modifiers like "(param=n)" kept), because keys never contain "/".
Leaves are strings, arrays and plural maps (zero/one/two/few/many/other). Merged files follow en.json's key order.
"""
import collections
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "app", "src", "main", "assets")
PLURAL = {"zero", "one", "two", "few", "many", "other"}


def folder(oldy):
    return os.path.join(ASSETS, "i18n-oldy" if oldy else "i18n")


def load(path):
    if not os.path.exists(path):
        return collections.OrderedDict()
    with open(path, encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=collections.OrderedDict)


def save(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def bare(key):
    return key.split("(")[0]


def is_leaf(v):
    if not isinstance(v, dict):
        return True
    return len(v) > 0 and set(v.keys()) <= PLURAL and all(isinstance(x, str) for x in v.values())


def find(obj, key):
    """Child of obj whose key matches ignoring modifiers."""
    for k, v in obj.items():
        if bare(k) == bare(key):
            return k, v
    return None, None


def leaves(obj, prefix=""):
    out = collections.OrderedDict()
    for k, v in obj.items():
        if k.startswith("@"):
            continue
        p = prefix + k
        if is_leaf(v):
            out[p] = v
        else:
            out.update(leaves(v, p + "/"))
    return out


def missing(locale, oldy):
    en = load(os.path.join(folder(oldy), "en.json"))
    loc = load(os.path.join(folder(oldy), locale + ".json"))
    out = collections.OrderedDict()
    for path, value in leaves(en).items():
        node = loc
        ok = True
        for part in path.split("/"):
            if not isinstance(node, dict):
                ok = False
                break
            _, node = find(node, part)
            if node is None or node == "" or node == []:
                ok = False
                break
        if not ok:
            out[path] = value
    return out


def ordered_like(en, loc):
    """Copy of loc with en's key order; keys only loc has are appended."""
    out = collections.OrderedDict()
    for k, v in en.items():
        lk, lv = find(loc, k)
        if lk is None:
            continue
        if isinstance(v, dict) and isinstance(lv, dict) and not is_leaf(v):
            out[lk] = ordered_like(v, lv)
        else:
            out[lk] = lv
    for k, v in loc.items():
        if k not in out:
            out[k] = v
    return out


def merge(locale, values, oldy):
    path = os.path.join(folder(oldy), locale + ".json")
    en = load(os.path.join(folder(oldy), "en.json"))
    loc = load(path)
    for p, value in values.items():
        parts = p.split("/")
        node = loc
        for part in parts[:-1]:
            k, child = find(node, part)
            if child is None or not isinstance(child, dict):
                k = k or part
                child = collections.OrderedDict()
                node[k] = child
            node = child
        k, _ = find(node, parts[-1])
        node[k or parts[-1]] = value
    save(path, ordered_like(en, loc))


def main():
    args = [a for a in sys.argv[1:] if a != "--oldy"]
    oldy = "--oldy" in sys.argv
    if not args:
        print(__doc__)
        return
    if args[0] == "missing":
        sys.stdout.reconfigure(encoding="utf-8")
        print(json.dumps(missing(args[1], oldy), ensure_ascii=False, indent=2))
    elif args[0] == "merge":
        merge(args[1], load(args[2]), oldy)
    elif args[0] == "report":
        for f in sorted(glob.glob(os.path.join(folder(False), "*.json"))):
            loc = os.path.basename(f)[:-5]
            print("%-10s localsend %3d  oldy %3d" % (loc, len(missing(loc, False)), len(missing(loc, True))))


if __name__ == "__main__":
    main()
