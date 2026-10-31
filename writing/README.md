# Writing

Posts about this project live here: one markdown file per post, named `YYYY-MM-DD-short-title.md`.

```
---
title: Post title
date: 2026-10-21
description: One sentence for lists and link previews.
categories: [Open Source, Hardware]
---

The post, in markdown.
```

elishajkmiller.com reads this folder when the site builds and publishes each post on this
project's page under Projects. Only projects listed in the website's `projects.json` that have a
public GitHub repo are read. A file whose name starts with `_` or `.` is a draft and is skipped.

