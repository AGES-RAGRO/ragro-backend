#!/bin/sh
set -e

# Set OLLAMA_HOST only for the server process (binds to all interfaces)
OLLAMA_HOST=0.0.0.0 /bin/ollama serve &
OLLAMA_PID=$!

echo "Ollama: waiting for server to be ready..."
for i in $(seq 1 30); do
    if ollama list > /dev/null 2>&1; then
        echo "Ollama: server ready."
        break
    fi
    printf "."
    [ "$i" -eq 30 ] && echo " ERROR: timeout" && exit 1
    sleep 1
done

if ollama list | grep -q "gemma2:2b"; then
    echo "Ollama: model gemma2:2b already cached. Skipping pull."
else
    echo "Ollama: model gemma2:2b not found. Pulling..."
    ollama pull gemma2:2b
    echo "Ollama: pull complete."
fi

wait $OLLAMA_PID
