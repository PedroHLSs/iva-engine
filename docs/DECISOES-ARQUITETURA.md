# Decisões de Arquitetura

Registro leve de ADRs. Uma decisão por etapa do projeto.

Formato: **Contexto** (o que motivou), **Decisão** (o que foi escolhido),
**Consequência** (o que passa a valer, incluindo o que fica mais difícil).

Status possíveis: `Aceita`, `Substituída por Dxxx`, `Revogada`.

---

## D001 — Domínio isolado de framework

**Etapa:** 0 — Fundação do repositório
**Status:** Aceita

### Contexto

O núcleo do sistema é um conjunto de regras de coerência sobre campos de IBS e
CBS de documentos fiscais. Essas regras são o objeto de estudo do TCC: são elas
que precisam ser lidas, discutidas e testadas com facilidade, tanto por quem
avalia o trabalho quanto por quem o mantém depois.

Duas forças puxavam em direções opostas:

1. O caminho de menor esforço em Spring Boot é anotar tudo — entidades JPA que
   também são o modelo de negócio, componentes Spring em toda parte, objetos de
   domínio que carregam anotações de serialização Jackson/JAXB porque em algum
   momento precisaram virar XML ou JSON.
2. Esse caminho amarra a regra de negócio ao ciclo de vida do framework. Testar
   uma regra passa a exigir contexto Spring; entender a regra passa a exigir
   entender o mapeamento; e uma troca de detalhe técnico (ORM, biblioteca de
   XML, formato de entrada) vaza para dentro da regra.

O projeto tem ainda uma característica que agrava o item 2: o conteúdo
normativo não é conhecido em tempo de compilação — ele entra por importação de
CSV em tempo de execução. As regras de domínio precisam ser expressas sobre
dados que chegam de fora, sem depender de como chegam.

### Decisão

O código é dividido em três pacotes sob `br.edu.tcc.auditoria`, com dependência
em sentido único:

```
infraestrutura ──▶ aplicacao ──▶ dominio
```

- `dominio` — regras de coerência, modelo dos documentos e dos apontamentos.
  **Não importa nada de Spring, JPA, Jackson ou JAXB.** Usa apenas a biblioteca
  padrão do Java.
- `aplicacao` — casos de uso e orquestração. Quando precisa de um recurso
  externo, declara uma interface e depende dela, não da implementação.
- `infraestrutura` — leitura de XML, acesso a PostgreSQL, importação de CSV,
  configuração Spring, entrada e saída.

### Consequência

Ganhos:

- As regras de auditoria são testáveis com JUnit puro, sem subir contexto
  Spring — testes rápidos e legíveis, que é o que o TCC precisa demonstrar.
- O modelo de domínio pode ser desenhado a partir do problema fiscal, e não a
  partir do que o ORM ou o parser de XML tornam conveniente.
- Trocar a forma de ler o XML ou de persistir não toca em regra de negócio.

Custos aceitos:

- Haverá duplicação estrutural: entidade de persistência e objeto de domínio
  serão tipos distintos, com tradução explícita entre eles na infraestrutura.
  Isso é deliberado, não é omissão a ser "corrigida" depois.
- Não se pode usar o atalho de anotar o objeto de domínio para serializar. Todo
  mapeamento para XML/JSON acontece na infraestrutura, em tipos próprios.
- A regra exige vigilância: uma única anotação de framework no domínio anula a
  decisão. Está registrada em `CLAUDE.md` como restrição permanente.

---
