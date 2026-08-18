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

## D003 — Toda consulta normativa é resolvida na data do documento

**Etapa:** 2 — Catálogo normativo e resolução por vigência
**Status:** Aceita

> Numeração: esta é a decisão que a Etapa 2 pediu sob o rótulo "D002". Como
> `D002` já estava ocupado pela Etapa 1, foi registrada como `D003`.

### Contexto

O sistema audita documentos já emitidos, e o conhecimento jurídico que ele
consulta muda ao longo do tempo. Um `cClassTrib` pode ter um conjunto de CSTs
compatíveis num período e outro no período seguinte; um NCM pode entrar e sair
de um anexo; uma alíquota é substituída. Auditar um documento emitido no ano
passado contra a versão de hoje do catálogo produz apontamento fundamentado em
norma que não valia quando o documento foi emitido.

Esse erro tem uma característica que o torna especialmente perigoso num TCC:
**ele é silencioso e o resultado parece plausível**. O relatório sai bem
formatado, com fundamento citado e valor calculado, e está errado. Nada falha,
nada avisa.

O caminho de menor esforço conduz direto a ele, por três portas:

1. o repositório expõe `buscarPorCodigo(codigo)` e alguém, mais tarde, precisa
   de "o registro atual";
2. uma regra chama `LocalDate.now()` porque a data não chegou até ela;
3. o catálogo guarda só a última versão de cada registro, porque foi assim que
   o CSV foi carregado.

Havia ainda um segundo problema, independente do primeiro: **catálogo com duas
versões do mesmo registro valendo na mesma data**. Quem consulta não tem como
saber qual das duas responder, e qualquer critério de desempate — a mais
recente, a primeira carregada, a de maior vigência — seria inventado por quem
escreveu o código, não pela norma.

### Decisão

**A data é fixada uma vez, na fronteira, e as regras não a veem.**

- `ContextoNormativo` é a única porta pela qual uma regra de auditoria consulta
  o catálogo. **Nenhum método dela aceita data como parâmetro**, e nenhum
  devolve data. A regra pergunta "o que o catálogo diz sobre este código?" e
  recebe a resposta já resolvida.
- `ContextoNormativoNaData` recebe a data de emissão do documento no construtor
  e a guarda em campo privado sem acessor. Não há construtor sem data.
- Os repositórios — `RepositorioClassificacaoTributaria`, `RepositorioNcm`,
  `RepositorioItemAnexo`, `RepositorioAliquota` — recebem `LocalDate` como
  parâmetro, porque é neles que a resolução acontece. Regra nenhuma fala com
  eles.
- Não há chamada a `LocalDate.now()` em nenhum ponto do domínio.
- Uma regra que precise registrar a vigência aplicada num `Achado` a toma do
  registro que consultou, via `RegistroNormativo.vigencia()` — a vigência que
  de fato sustentou o apontamento, e não uma data solta que a regra teria de
  correlacionar por conta própria.

**Vigência e fonte moram num tipo só.** `ProcedenciaNormativa` reúne
`vigenciaInicio`, `vigenciaFim` e `fonteNormativa`, e todo registro do catálogo
a carrega através da interface `RegistroNormativo`. Não é economia de digitação:
é a garantia de que um tipo novo de registro não possa nascer sem data ou sem
fonte. Registro sem vigência não pode ser resolvido no tempo; registro sem fonte
gera apontamento que ninguém consegue conferir.

**Sobreposição de vigência é erro de catálogo, e falha na carga.**
`SerieNormativa` agrupa as versões de uma mesma chave e se recusa a existir se
duas delas valerem na mesma data. A invariante fica no domínio, não no
repositório: trocar armazenamento em memória por PostgreSQL não pode ser
oportunidade de perdê-la. A carga falha inteira, e não parcialmente — catálogo
meio carregado responderia vazio para o que faltou, e vazio significa "o
catálogo nada diz", de modo que um erro de carga sairia no relatório disfarçado
de `NAO_AVALIADO`.

**A chave da série é a identidade completa do registro, não um campo isolado.**
Item de anexo tem chave `NCM + anexo`; alíquota tem chave
`tributo + abrangência`. Se a chave do item de anexo fosse só o NCM, o catálogo
recusaria como "sobreposição" um NCM vinculado a dois anexos ao mesmo tempo — o
que equivaleria a o código afirmar que isso não pode acontecer. Quem afirma o
que pode e o que não pode é a fonte importada.

**Consulta sem resposta devolve vazio, nunca a versão mais próxima.** Data
anterior à primeira vigência, posterior à última, ou caída num intervalo
descoberto entre duas versões: todas devolvem vazio. Vazio significa "o catálogo
nada diz nesta data" e leva a `NAO_AVALIADO` — nunca a conformidade, e nunca a
um chute pela versão vizinha.

**O catálogo entra vazio e só por importação.** Os quatro importadores CSV
recusam qualquer linha sem `vigenciaInicio` ou sem `fonteNormativa`, sempre
indicando o número da linha física do arquivo — quem opera o catálogo não é quem
escreveu o código, e "linha 7 sem fonteNormativa" é acionável enquanto "erro de
importação" não é. Quando um valor lido viola invariante do domínio, a exceção
do domínio vai como causa e a mensagem acrescenta a linha.

### Consequência

Ganhos:

- É estruturalmente impossível uma regra de auditoria consultar o catálogo numa
  data que não seja a do documento. Não depende de disciplina de quem escreve a
  regra, e há teste por reflexão que falha se algum método de
  `ContextoNormativo` passar a aceitar ou devolver data.
- O mesmo documento auditado hoje e daqui a dois anos produz o mesmo relatório,
  desde que o catálogo não seja reescrito retroativamente.
- Todo apontamento consegue citar a fonte e a vigência exatas que o
  sustentaram, porque o registro consultado as carrega.
- Catálogo contraditório falha alto, na carga, e não silenciosamente na
  consulta.

Custos aceitos:

- A camada de aplicação passa a ter uma responsabilidade obrigatória: construir
  um `ContextoNormativo` por documento auditado. Não há contexto global nem
  reaproveitável entre documentos de datas diferentes.
- Consultar "o que vale hoje" não é uma operação que o sistema ofereça a regras.
  Se algum dia for preciso — para uma tela de conferência de catálogo, por
  exemplo — isso será um caso de uso próprio na aplicação, com data explícita,
  e não um método novo em `ContextoNormativo`.
- Corrigir uma linha errada do catálogo exige entender vigências: não se
  sobrescreve o registro, fecha-se a vigência anterior e abre-se a seguinte.
  Cargas descuidadas falham em vez de "funcionar".
- O leitor de CSV é próprio, sem biblioteca. Suporta aspas e separador `;`, mas
  não campo com quebra de linha — o que manteria a numeração de linha honesta
  deixaria de valer, e a numeração é o que torna a recusa acionável.

---

## D004 — Silêncio do catálogo só vira apontamento dentro da cobertura declarada

**Etapa:** 3 — Motor de regras
**Status:** Aceita

### Contexto

As regras de auditoria perguntam ao catálogo e reportam o que ouvem. A regra de
ouro da etapa é clara: **se o catálogo não tiver dado suficiente para julgar o
item, o resultado é `NAO_AVALIADO` com motivo, jamais `CONFORME`.** Ausência de
dado e conformidade não podem se parecer na saída.

Ao escrever as sete regras, apareceu um caso em que essa regra, aplicada ao pé
da letra, se autodestrói. R01 pergunta se o `cClassTrib` declarado consta do
catálogo, e R06 pergunta o mesmo do NCM. Nessas duas, "o catálogo nada diz" é
exatamente o achado que se quer produzir. Se elas devolvessem `NAO_AVALIADO`
sempre que o código não fosse encontrado, jamais apontariam coisa alguma — e o
sistema perderia a verificação mais elementar que tem.

O inverso é igualmente ruim. `ContextoNormativo.classificacaoTributaria(codigo)`
devolve vazio tanto quando a tabela foi carregada e o código não consta dela
quanto quando a tabela não foi carregada. Um catálogo vazio faria R01 e R06
acusarem todo item de todo documento, com aparência de conclusão firme. Numa
ferramenta de auditoria, apontamento falso é pior que apontamento faltante: o
primeiro acusa, o segundo se mostra.

Havia ainda um problema menor de mesma origem. `Achado` exige
`fundamentoNormativo` e `vigenciaAplicada`, e D003 manda tomá-los do registro
consultado. Em R01 e R06 não há registro consultado — a inexistência dele é o
achado.

O mesmo dilema aparece, em outra forma, em R03 e R04, que leem o vínculo entre
NCM e anexo: tabela de anexos não carregada faria R03 apontar todo benefício
como sem respaldo e faria R04 dar todo item por conforme.

### Decisão

**Quem carrega o catálogo declara, por tabela, a cobertura daquela carga** —
vigência e fonte, reunidas na `ProcedenciaNormativa` que o resto do sistema já
usa, agrupadas em `CoberturaDoCatalogo` para as três tabelas que precisam dela.

- **Dentro da cobertura**, silêncio do catálogo é resposta: o código não consta,
  e isso vira apontamento.
- **Fora da cobertura**, silêncio é falta de dado: `NAO_AVALIADO`, com motivo
  dizendo qual período a carga cobre e qual é a data de emissão.

Os valores não são escritos em código: chegam de fora, junto dos CSVs a que se
referem, como todo o resto do conteúdo normativo. A cobertura é também o que dá
fundamento e vigência ao apontamento de "não consta", já que não há registro de
onde tomá-los.

Decorrem daí as demais escolhas da etapa:

- **`Avaliacao` é tipo selado em três variantes**, uma por `ResultadoAvaliacao`.
  `Conforme` não tem campo de texto; `NaoAvaliada` exige motivo não vazio;
  `ComAchado` exige o `Achado`. É impossível construir uma avaliação que diga
  "não avaliei" sem dizer por quê, e as três se distinguem pelo tipo, sem
  inspeção de texto.
- **Toda avaliação se identifica** — regra, versão, documento e item. Um
  `NAO_AVALIADO` solto não diria a que item se refere, e relatório de auditoria
  não tem linha sem endereço.
- **`NAO_AVALIADO` continua na saída do motor.** Nada é filtrado. Devolver só os
  achados faria o relatório perder a contagem de regras que não puderam ser
  aplicadas, que é o que distingue auditoria honesta de auditoria que parece
  limpa.
- **Ordem determinística por construção**: chave de acesso, número do item,
  posição da regra no conjunto. A ordenação por chave torna a saída independente
  da ordem de entrada, que costuma vir de listagem de diretório ou de consulta a
  banco e não promete estabilidade. Onde uma regra lê coleção sem ordem
  garantida — o conjunto de CSTs compatíveis, a lista de anexos de um NCM — ela
  ordena antes de montar a evidência.
- **O motor recebe um `ProvedorDeContextoNormativo`, não um contexto.**
  Documentos de datas diferentes não podem compartilhar catálogo resolvido, sob
  pena de reintroduzir o erro que D003 fecha. A variante de documento único
  recebe o contexto direto, porque ali só há uma data.
- **`ConjuntoRegras` tem versão própria** (`2026.1`), separada da versão de cada
  regra. Acrescentar, remover ou reordenar regras muda o relatório sem mudar
  versão de regra nenhuma. Como a obrigação de subir a versão não tem como ser
  garantida pelo compilador, há teste que fixa o inventário do conjunto padrão:
  mexeu em regra, o teste falha, e a decisão de versionamento é tomada na hora.
- **R07 precisa de vocabulário publicado.** O catálogo cita campos exigidos por
  nome, em texto. `CampoDoItem` é esse vocabulário, e usa exatamente os nomes dos
  componentes de `ItemDocumento`, com teste por reflexão que falha se algum for
  renomeado. Nome que o vocabulário não reconhece não é adivinhado: vira
  `NAO_AVALIADO` citando o nome, ou evidência dentro do achado quando já houver
  campo conhecido faltando.

### Consequência

Ganhos:

- R01 e R06 apontam de verdade, e param de apontar quando a carga não cobre a
  data — sem que a diferença dependa de disciplina de quem escreve a regra.
- Todo apontamento continua citando fonte e vigência, inclusive os que nascem da
  ausência de registro.
- Regra nova acrescentada ao conjunto passa a ser cobrada automaticamente pelo
  teste transversal da regra de ouro, sem depender de alguém lembrar de escrever
  o teste específico.
- Dois relatórios do mesmo lote podem ser comparados linha a linha.

Custos aceitos:

- Quem opera o catálogo tem uma obrigação a mais: declarar a cobertura de cada
  carga. Declarar cobertura para uma tabela que não foi carregada faz o sistema
  apontar; é erro de operação, e é visível.
- **R03 verifica vínculo do NCM a _algum_ anexo, não ao anexo correspondente ao
  `cClassTrib`.** O catálogo da Etapa 2 não guarda o vínculo
  `cClassTrib → anexo`. A verificação implementada é condição necessária da
  pretendida, e deixa passar o caso do NCM que consta de anexo diferente do que
  o código invocado pressupõe. Fechar isso exige acrescentar o vínculo ao
  catálogo — mudança em etapa entregue, não feita aqui.
- **R05 não escolhe abrangência.** Quando o catálogo traz mais de uma alíquota
  vigente para o mesmo tributo na data, a regra devolve `NAO_AVALIADO` nomeando
  as abrangências. Escolher uma seria afirmar qual se aplica a qual operação, o
  que é conteúdo normativo.
- **R05 lê o percentual do catálogo como porcentagem**, isto é, o valor esperado
  é `base × percentual ÷ 100`. É convenção de unidade da coluna importada, e
  quem prepara o CSV precisa saber dela.
- **R02 não trata conjunto vazio de CSTs compatíveis como "nenhum CST é
  admitido".** Poderia ser lido assim, o que tornaria todo item um achado; mas
  coluna preenchida em branco é indistinguível de coluna que ninguém preencheu, e
  transformar essa dúvida em acusação é o oposto do que uma auditoria faz. Fica
  `NAO_AVALIADO`, visível no relatório e corrigível na carga.
- Testes de regra usam um construtor abreviado de `ItemDocumento` que o modelo de
  produção recusa (D002). Ele existe apenas em `src/test`, e o padrão de todo
  campo opcional nele é `Optional.empty()` — nenhum campo aparece preenchido sem
  que o teste tenha pedido, e nenhum zero surge de padrão.

---
