# rag

A Spring Boot service that answers questions over a private document collection using a locally hosted LLM. Nothing leaves the machine — both the embedding model and the chat model run under Ollama.

## Purpose

Standard LLMs answer from what they memorised during training. This service instead retrieves the relevant passages from your own documents at query time and asks the model to answer using only those passages. That means:

- Answers reflect your documents, not the model's training data
- Every answer can point back to the source passage it came from
- Documents can be added or changed without retraining anything
- No data is sent to an external API

## How it works

```
Document  →  chunk  →  embed (bge-m3)  →  vector store
                                              │
Question  →  embed  →  similarity search  ────┘
                                              │
                             top-k passages + question
                                              │
                                              ▼
                              chat model (Krikri 8B)  →  answer
```

Two phases:

**Ingestion** — documents are split into chunks, each chunk is turned into a vector by the embedding model, and the vectors are stored for search.

**Query** — the incoming question is embedded the same way, the closest chunks are retrieved, and those chunks are placed into the prompt as context for the chat model.

## Stack

| Component | Choice | Why |
|---|---|---|
| Framework | Spring Boot + Spring AI 2.x | Java service, standard Spring config and DI |
| Inference | Ollama (local) | On-premise, no external API calls |
| Chat model | `ilsp/llama-krikri-8b-instruct` | Greek-language instruction model |
| Embeddings | `bge-m3` | Multilingual, handles Greek text |

## Prerequisites

- Java 21+
- Ollama running locally
- Models pulled:

```bash
ollama pull ilsp/llama-krikri-8b-instruct:latest
ollama pull bge-m3:latest
```

Roughly 8 GB of free RAM is needed to hold the 8B model plus its context.

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=rag
spring.ai.model.chat=ollama
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.chat.model=ilsp/llama-krikri-8b-instruct:latest
spring.ai.ollama.chat.temperature=0.7
spring.ai.ollama.chat.keep-alive=-1s
spring.ai.ollama.chat.num-ctx=2048
```

Notes:

- `keep-alive=-1s` keeps the model resident between requests. Without it the first request after an idle period waits for a full model reload and often times out.
- `num-ctx` sizes the context window. Larger values need noticeably more memory for the KV cache.
- In Spring AI 2.x the `spring.ai.ollama.chat.options.*` prefix is deprecated; properties sit directly under `spring.ai.ollama.chat.*`.

## Running

```bash
ollama serve          # if not already running as a service
./mvnw spring-boot:run
```

## Troubleshooting

**`Connection prematurely closed BEFORE response`** — Ollama did not respond in time, usually because the model was being loaded from cold. Set `keep-alive`, preload the model with `ollama run <model>`, and raise the HTTP client read timeout.

**`EOF` or `Empty reply from server` from `curl` or `ollama run`** — the Ollama process itself is failing, independent of this application. Usually memory: check `free -h` and `dmesg -T | grep -i "killed process"`. A smaller model or a lower `num-ctx` will confirm it.

**`missing unit in duration "-1"`** — Ollama expects a Go duration string. Use `-1s`, not `-1`.

## Status

Early development. Not yet implemented:

- Document ingestion pipeline
- Persistent vector store
- Source citations in responses
- Access control on retrieval
- Evaluation harness