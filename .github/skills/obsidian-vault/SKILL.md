---
name: obsidian-vault
description: "Work directly with the Obsidian vault at /home/oleh/vaults/vaulmain. Use when: updating Obsidian notes, writing sprint summaries, updating project status, adding architecture notes, creating new pages in the vault, syncing code changes to Obsidian documentation. Triggers: обнови obsidian, запиши в obsidian, обнови заметки, update obsidian, update vault, sync to obsidian."
argument-hint: "What to update: index | current-state | architecture | roadmap | all"
---

# Obsidian Vault Skill

## Vault Setup

\`/home/oleh/vaults/vaulmain\` — **symlink** → \`/mnt/c/Users/Oleh/Documents/vaulmain\`

Оба Obsidian (WSL \`/usr/bin/obsidian\` и Windows) открывают **один и тот же vault**.
Backup старого WSL-vault: \`/home/oleh/vaults/vaulmain.bak\`.

**Синхронизация между устройствами** — через **Dropbox** (плагин Obsidian Sync или сторонний Dropbox-плагин).
Изменения сделанные через Python-скрипт автоматически подхватываются Dropbox и расходятся на все устройства.

## Access Method

Vault за Windows-mount — **shell зависает, только Python**.

\`\`\`bash
python3 /home/oleh/AgentShell/scripts/update_obsidian.py
\`\`\`

Inline для одного файла:
\`\`\`python
python3 -c "
with open('/home/oleh/vaults/vaulmain/AgentShell/...', 'w', encoding='utf-8') as f:
    f.write(content)
"
\`\`\`

## Vault Structure

\`\`\`
vaulmain/   → /mnt/c/Users/Oleh/Documents/vaulmain
├── AgentShell/
│   ├── 00_Index/AgentShell - Index.md
│   ├── 10_Architecture/AgentShell - Architecture Overview.md
│   ├── 20_Workflows/
│   ├── 30_Operations/
│   └── 40_Roadmap/
│       ├── AgentShell — Текущее состояние и следующие шаги.md
│       └── AgentShell — расширенный общий план.md
├── Projects/  Tech/  Inbox/  Clippings/  env/  subtxtlab/
\`\`\`

## Procedure

### 1. Verify
\`\`\`python
python3 -c "import pathlib; print(pathlib.Path('/home/oleh/vaults/vaulmain/AgentShell').exists())"
\`\`\`

### 2. Read
\`\`\`python
python3 -c "print(open('/home/oleh/vaults/vaulmain/AgentShell/00_Index/AgentShell - Index.md', encoding='utf-8').read())"
\`\`\`

### 3. Write (through update_obsidian.py or inline Python)

### 4. Check size
\`\`\`python
python3 -c "import pathlib; p=pathlib.Path('/home/oleh/vaults/vaulmain/AgentShell/00_Index/AgentShell - Index.md'); print(p.stat().st_size, 'bytes')"
\`\`\`

## Key Notes

- **Только Python** — shell/heredoc зависает на Windows-mount
- Obsidian links: \`[[double brackets]]\`
- Файлы UTF-8, Unix line endings
- \`scripts/update_obsidian.py\` — BASE = \`/home/oleh/vaults/vaulmain/AgentShell\`

## Note Templates

### Sprint summary
\`\`\`markdown
### Sprint N — Title ✅
| Файл | Что сделано |
|---|---|
| \`File.kt\` | Description |
\`\`\`

### Roadmap priority: 🔴 critical · 🟠 high · 🟡 medium · 🟢 low
