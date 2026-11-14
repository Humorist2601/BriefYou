"""Makes a Brief You (Read You) database with a lot of articles, in the schema the app expects.

  python3 make_db.py OUT.db [articles] [feeds]
"""
import json, os, random, sqlite3, sys, time

out = sys.argv[1]
n_articles = int(sys.argv[2]) if len(sys.argv) > 2 else 12000
n_feeds = int(sys.argv[3]) if len(sys.argv) > 3 else 60
schema = json.load(open(os.path.join(os.path.dirname(__file__), "..", "app", "schemas",
                                      "me.ash.reader.infrastructure.db.AndroidDatabase", "7.json")))["database"]
if os.path.exists(out):
    os.remove(out)
db = sqlite3.connect(out)
for e in schema["entities"]:
    db.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    for i in e.get("indices", []):
        db.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))
for q in schema["setupQueries"]:
    db.execute(q)
db.execute("PRAGMA user_version = 7")
random.seed(3)
GROUPS = ["Daily Briefs", "Games", "News", "Technology", "Music", "Comics"]
db.execute("INSERT INTO account (id,name,type,updateAt) VALUES (1,'Local',1,?)", (int(time.time() * 1000),))
gids = []
for i, g in enumerate(GROUPS):
    gid = f"1${i:08d}-0000-0000-0000-000000000000"
    gids.append(gid)
    db.execute("INSERT INTO `group` (id,name,accountId) VALUES (?,?,1)", (gid, g))
fids = []
for i in range(n_feeds):
    fid = f"1$f{i:08d}-0000-0000-0000-000000000000"
    fids.append(fid)
    full = 1 if i % 5 == 0 else 0
    db.execute("INSERT INTO feed (id,name,icon,url,groupId,accountId,isNotification,isFullContent,isBrowser) VALUES (?,?,?,?,?,1,0,?,0)",
               (fid, f"Feed {i + 1}", None, f"https://example.invalid/feed{i}.xml", gids[i % len(gids)], full))
words = ("alpha beta gamma delta lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor "
         "incididunt ut labore et dolore magna aliqua").split()
def para(k):
    return "<p>" + " ".join(random.choice(words) for _ in range(k)) + "</p>"
now = int(time.time() * 1000)
rows = []
for i in range(n_articles):
    feed = random.choice(fids)
    body = "".join(para(random.randint(40, 120)) for _ in range(random.randint(3, 14)))  # about 2 to 10 KB
    full = "".join(para(random.randint(60, 140)) for _ in range(random.randint(10, 40))) if i % 5 == 0 else None
    unread = 1 if random.random() < 0.2 else 0
    rows.append((f"1$a{i:09d}", now - i * 240_000, f"Article {i}: " + " ".join(random.choice(words) for _ in range(7)).title(),
                 "Author", body, body[:180], full, None, f"https://example.invalid/a/{i}", feed, 1, unread,
                 1 if i % 97 == 0 else 0, 0, now - i * 240_000))
db.executemany("INSERT INTO article VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
db.commit()
db.execute("PRAGMA journal_mode = DELETE")
db.close()
print(out, os.path.getsize(out) // 1_000_000, "MB,", n_articles, "articles")
