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

## D002 — Ausência é um estado, não um zero

**Etapa:** 1 — Domínio puro
**Status:** Aceita

### Contexto

O sistema audita documentos já emitidos. A matéria-prima é o que o contribuinte
declarou — e, com igual importância, o que ele **não** declarou. Um item sem
base de cálculo de IBS e um item com base de cálculo declarada como zero são
situações fiscalmente distintas: a primeira é omissão de campo, a segunda é
informação prestada. Elas produzem apontamentos diferentes.

O caminho de menor esforço apaga essa diferença em três lugares distintos, e em
todos eles por conveniência técnica:

1. leitura de XML — campo ausente vira `null`, que vira `0` na primeira
   operação aritmética;
2. mapeamento de banco — coluna `NOT NULL DEFAULT 0`;
3. assinatura de método — `BigDecimal getBaseCalculoIbs()` não tem como
   devolver "não veio".

Se qualquer um dos três converter ausência em zero, o sistema passa a afirmar
que o contribuinte declarou algo que ele não declarou. Num TCC, isso não é um
bug de arredondamento: é o relatório mentindo sobre o documento.

Havia ainda uma segunda pressão, do mesmo tipo: o modelo precisa guardar quem
emitiu e quem recebeu o documento, mas o repositório processa notas reais de uma
empresa, e CNPJ, CPF, razão social e endereço não podem circular pelo núcleo do
sistema.

### Decisão

**Ausência tem uma grafia só.** Todo campo que pode não vir no documento é
`Optional<T>` no domínio. `null` e texto em branco são rejeitados na construção,
com exceção de domínio, e não normalizados para vazio: aceitar uma segunda
grafia de ausência traria de volta o problema por outra porta.

Decorrem daí quatro escolhas concretas:

- **Sem construtor abreviado e sem valor padrão.** Não há builder com defaults
  em `ItemDocumento` nem em `Documento`. Quem monta um item é obrigado pelo
  compilador a dizer, campo a campo, se o dado veio ou não. A verbosidade no
  ponto de construção é o preço de não haver omissão silenciosa.
- **`BigDecimal` em todo valor monetário, nunca `double`**, com a escala
  declarada preservada como veio. Para a auditoria, `0` e `0,00` são registros
  diferentes do mesmo número. Como `BigDecimal.equals` distingue escala,
  comparação de grandeza nas regras usa `compareTo`.
- **`ResultadoAvaliacao.NAO_AVALIADO` é um desfecho de primeira classe**, ao
  lado de `ACHADO` e `CONFORME`. Regra que não pôde ser aplicada — faltou campo,
  faltou tabela, data fora de vigência — não é regra que não encontrou nada.
- **`ValorEmRisco` é tipo selado**, com as variantes `Calculado` e
  `NaoCalculavel`. Um `Optional<BigDecimal>` solto permitiria vazio sem
  explicação; aqui `NaoCalculavel` exige o motivo no construtor, e é impossível
  registrar ausência de valor sem dizer por quê.

**Participantes só entram pseudonimizados.** `IdentificadorPseudonimizado`
aceita exclusivamente resumo criptográfico de 256 bits em hexadecimal minúsculo:
64 caracteres em `0-9a-f`. Nenhum CNPJ, CPF, razão social ou endereço satisfaz
esse formato, de modo que a tentativa de guardar o valor original falha na
construção. O cálculo do resumo fica na infraestrutura, única camada que vê o
dado real. O domínio não tem campo para o valor em texto claro e não sabe
reverter o pseudônimo — para auditar coerência de IBS/CBS basta saber se dois
documentos têm o mesmo participante, e a igualdade do pseudônimo responde isso.

**Validação estrutural, nunca normativa.** Os objetos de valor validam apenas a
forma, e apenas quando a forma é sabidamente independente da legislação:
`ChaveAcesso` 44 dígitos, `Ncm` 8, `Cfop` 4. `CodigoCst` e
`CodigoClassificacaoTributaria` **não** validam conjunto de códigos nem
comprimento: essa tabela é conteúdo normativo, chega por importação de CSV e é
confrontada pelas regras, não pelo construtor. `Uf` é enum fechado das 27
unidades federativas — divisão político-administrativa, não matéria tributária,
e sem nenhuma alíquota, código ou vigência associada. Nenhuma data está fixada
em código: `PeriodoVigencia` recebe as suas das tabelas importadas.

Pela mesma razão, campos numéricos não são validados contra faixa ou sinal. O
sistema precisa conseguir representar inclusive o que está errado, sob pena de
não ter o que apontar.

**Documento e item são tipos separados.** `Documento` não carrega lista de
itens; uma regra que precise dos dois recebe o par, e compô-los é tarefa da
aplicação.

**A regra D001 passa a ser verificada por teste.** `DominioNaoDependeDeFramework`
lê os fontes de `dominio/` e falha se algum importar qualquer coisa fora de
`java.*` e do próprio domínio.

### Consequência

Ganhos:

- A distinção entre omissão e declaração sobrevive da leitura ao relatório, e
  está coberta por teste dedicado (`CampoAusenteNaoEZero`).
- Um apontamento sem evidência, sem fundamento normativo, sem versão de regra ou
  com valor ausente e inexplicado não compila nem constrói.
- Dado pessoal em texto claro não tem por onde entrar no núcleo.
- A proibição de framework no domínio deixou de depender de vigilância humana.

Custos aceitos:

- Construir um `ItemDocumento` exige quinze argumentos explícitos, quase todos
  `Optional`. É verboso de propósito e não deve ser "resolvido" com builder de
  defaults.
- A infraestrutura fica com trabalho que o domínio se recusa a fazer: aparar
  espaços, normalizar maiúsculas, calcular o resumo criptográfico, e decidir o
  que fazer com documento cuja chave ou cujo NCM não têm sequer a forma
  esperada.
- Nenhuma regra de auditoria pode ser escrita antes de as tabelas normativas
  existirem. Isso é intencional: sem tabela, a saída correta é `NAO_AVALIADO`.

---
