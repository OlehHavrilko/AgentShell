#!/data/data/com.termux/files/usr/bin/sh
cd ~/agentshell
exec java -jar agent-core.jar \
    --mcp-socket /data/data/com.termux/files/home/agentshell_socket \
    "$@"
