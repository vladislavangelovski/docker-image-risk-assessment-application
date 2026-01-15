#!/usr/bin/env bash
set -euo pipefail

embed_model="${OLLAMA_EMBED_MODEL:-nomic-embed-text}"
chat_model="${OLLAMA_CHAT_MODEL:-llama3.2:3b-instruct-q4_K_M}"

echo "Pulling Ollama models (this can take a while)..."
echo " - embeddings: ${embed_model}"
echo " - chat: ${chat_model}"

docker compose exec -T ollama ollama pull "${embed_model}"
docker compose exec -T ollama ollama pull "${chat_model}"

echo "Done. Installed models:"
docker compose exec -T ollama ollama list

