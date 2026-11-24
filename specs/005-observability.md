# Spec 005 — Observabilidade e auditoria
Status: Rascunho | Depende de: 003 (idealmente 004)

## Contexto
Para operar e depurar um agente é preciso ver cada execução: passos, custo,
latência e decisões. Esta spec padroniza traces, métricas e replay.

## User story
Como dev, quero rastrear e reexecutar o raciocínio do agente, para depurar
decisões erradas e acompanhar custo e qualidade.

## Critérios de aceite
- [ ] Um trace por mensagem; um span por passo do agente e por chamada de tool,
  com atributos `run.id`, `message.id`, `tool.name`.
- [ ] Traces exportados via OTLP e visíveis no Jaeger local.
- [ ] Métricas Micrometer expostas em `/actuator/prometheus`:
  `dlq.messages.consumed`, `dlq.decision.count{action,class}`,
  `agent.run.duration`, `agent.steps`, `agent.tokens`, `agent.cost`.
- [ ] Logs estruturados com `traceId`, `messageId`, `runId`.
- [ ] Nenhum log INFO contém payload completo (teste automatizado).
- [ ] Comando/endpoint de replay reexecuta uma `agent_run` gravada com o LLM
  falso, reproduzindo as respostas registradas.
- [ ] Health checks: banco, Kafka, LLM.
- [ ] README tem 3 queries/painéis de exemplo para as métricas.

## Fora de escopo
Alertas, dashboards em produção, retenção de longo prazo.

## Notas técnicas e dependências
- Registro de passos já existe na spec 003; aqui são correlação e exportação.
- Contagem de custo: tokens × tabela de preço configurável (0 no `local`).

## Plano de teste
- Unitário: cálculo de custo, mascaramento de logs.
- Integração: existência dos spans/métricas após uma execução; replay.
- Manual: abrir o Jaeger e seguir uma mensagem do consumo à decisão.

## DoD
Critérios marcados; trace completo de uma mensagem demonstrado; testes e lint
verdes.