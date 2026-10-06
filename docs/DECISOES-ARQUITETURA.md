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
lê os fontes de `dominio/` e falha se algum referenciar qualquer coisa fora de
`java.*` e do próprio domínio. A verificação é pela positiva — lista do que é
permitido, não do que é proibido —, de modo que framework que ainda não existe no
projeto já cai nela.

São duas varreduras, porque a dependência tem duas grafias: a linha de `import`,
e o nome totalmente qualificado escrito no corpo do arquivo. A segunda entrou na
revisão de conformidade da Etapa 1, em 23/08/2026 — até então um
`@org.springframework.stereotype.Component` não gerava linha de `import` nenhuma
e passava. Ela descarta comentários e literais de texto antes de checar, de modo
que citar um framework para explicar por que ele não entra continua permitido.

Um terceiro teste afirma que a varredura encontrou arquivos: sem ele, um erro de
caminho faria as outras duas passarem por não terem olhado nada — que é a forma
mais silenciosa de um guarda deixar de guardar.

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

> **Emendada pela D015 (03/10/2026).** O critério que esta decisão deu à R02 —
> coluna em branco é indistinguível de coluna que ninguém preencheu, e fica
> `NAO_AVALIADO` — passou a valer também para a R07, que até essa data lia a
> lista vazia de campos exigidos como "nenhum campo exigido" e respondia
> `CONFORME`. O texto abaixo fica como foi decidido na Etapa 3.

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

## D005 — A fronteira do XML: leiaute gerado, ausência preservada, participante pseudonimizado

**Etapa:** 4 — Leitura de XML e normalização
**Status:** Aceita

### Contexto

Esta etapa liga o sistema ao mundo. Até aqui o domínio recebia dados montados à
mão em teste; agora ele recebe o que uma empresa real emitiu. A fronteira entre
o arquivo e o modelo é onde quatro coisas dão errado ao mesmo tempo, e todas as
quatro por conveniência.

**Primeira: o leiaute vira código.** Escrever o parser à mão significa digitar
`getElementsByTagName("vBC")` e afirmar, em Java, o que a norma diz que o
documento contém. Cada nome de elemento escrito à mão é uma afirmação sobre o
leiaute que ninguém conferiu, e que envelhece na primeira nota técnica. O mesmo
vale para "quase à mão": biblioteca genérica de XML com caminhos em texto.

**Segunda: ausência vira zero.** Toda biblioteca de XML devolve `null` para
elemento que não veio, e `null` vira `0` na primeira conta ou na primeira coluna
`NOT NULL DEFAULT 0`. D002 existe justamente para separar "o contribuinte não
declarou" de "o contribuinte declarou zero", e é aqui — no ponto onde o dado
entra — que essa distinção se perde ou se preserva. Perdida aqui, nenhuma regra
adiante consegue recuperá-la.

**Terceira: o CNPJ entra junto.** O documento traz CNPJ, CPF, razão social e
endereço de quem emitiu e de quem recebeu. O repositório processa notas reais de
uma empresa. Se esses valores atravessarem a fronteira, passam a existir em todo
objeto, todo log, toda mensagem de erro e todo relatório do sistema.

**Quarta: um arquivo ruim derruba o lote.** Acervo real tem arquivo truncado
pela metade, arquivo que na verdade é um evento de cancelamento, arquivo com
campo fora da forma esperada. Um laço que lê tudo e explode no primeiro problema
processa quinhentos documentos e não entrega nenhum.

### Decisão

**O leiaute não é escrito, é gerado.** As classes de leitura vêm do XSD oficial
do Portal Nacional da NF-e, compilado pelo `jaxb2-maven-plugin` para o pacote
`infraestrutura.xml.gerado` em `target/generated-sources`. Nenhum nome de
elemento do documento fiscal é digitado neste repositório fora do XSD — a
exceção é o próprio ponto de tradução, e ela é única e localizada.

> **Este parágrafo foi superado.** Os XSD são versionados e são cinco, e a única
> fonte do plugin é `nfe_v4.00.xsd` — ver a revisão de 03/09/2026 ao final desta
> decisão. O texto abaixo fica como registro do que foi decidido na Etapa 4.

Os XSD **não são versionados por padrão** e não são reproduzidos aqui: quem
clona baixa o Pacote de Liberação do Portal e copia seis arquivos para
`src/main/resources/schemas`, conforme o `LEIAME.md` de lá. Só os dois esquemas
raiz — `procNFe_v4.00.xsd` e `nfe_v4.00.xsd` — entram na configuração do plugin;
os outros quatro chegam por `xs:include`. Listar todos faria o XJC compilar o
mesmo esquema duas vezes e falhar com colisão de nomes na `ObjectFactory`.

**As classes geradas param na fronteira.** Só `infraestrutura.xml` as enxerga, e
há teste (`ClassesGeradasNaoVazam`) que varre o código de produção fora desse
pacote e falha se alguém referenciar o pacote gerado, `jakarta.xml.bind` ou
`javax.xml.stream`. Sem isso, o primeiro caso de uso que aceitar um `TNFe` por
parâmetro transforma a versão do esquema em parte da assinatura do sistema.

São duas varreduras: a linha de `import` e o nome totalmente qualificado escrito
no corpo do arquivo, este descartando comentários e literais de texto. A segunda
entrou em 03/09/2026 — até então um retorno declarado como
`br.edu.tcc.auditoria.infraestrutura.xml.gerado.TNFe` atravessava a fronteira sem
ser visto. Verificado nas duas direções com arquivo de sonda temporário.

**Ausência atravessa como ausência.** Campo que não veio e campo em branco viram
`Optional.empty()`; campo declarado como `0` vira `Optional.of(ZERO)`. Os valores
são construídos a partir do texto do XML, o que preserva a escala declarada —
`99.99` e `9.9900` chegam ao domínio com as casas que o documento escreveu.
Percentual entra como declarado, sem conversão para fração.

**Onde o leiaute traz um campo e o domínio tem dois, o valor é repetido.** O
grupo `IBSCBS` do item declara um `CST`, um `cClassTrib` e um `vBC` únicos,
válidos para os dois tributos; `ItemDocumento` tem campo separado para IBS e
para CBS. A normalização preenche os dois com o valor declarado, porque foi isso
que o documento afirmou dos dois. Deixar o lado da CBS vazio faria as regras
apontarem ausência de campo que o contribuinte preencheu — apontamento falso, que
é o pior defeito possível numa ferramenta de auditoria.

**Participante só entra pseudonimizado, e o sal vem de fora.** CNPJ e CPF passam
por SHA-256 com um sal de instalação que **não tem valor padrão**: sem ele
configurado, o sistema para. Sal fixo em código estaria no repositório e no jar,
e o espaço de CNPJ é pequeno o bastante para ser percorrido inteiro por força
bruta — pseudônimo com sal público é reversível, e portanto não é
pseudonimização. A garantia é verificada por varredura: o teste percorre o objeto
normalizado inteiro por reflexão, campo a campo, dentro de `Optional` e de lista,
e falha se encontrar o identificador que entrou pelo XML.

**A chave de acesso é preservada como veio, e isso tem preço.** Ela é a
identidade do documento auditado: é o que liga o apontamento ao arquivo e é o que
o contribuinte usa para conferir. Também carrega o CNPJ do emitente nas posições
intermediárias, por definição do leiaute. Logo: **documento com participantes
pseudonimizados não é documento anônimo.** A varredura ignora a chave, de
propósito e com comentário no teste, para não fingir uma garantia que o sistema
não dá.

**Documento que o domínio não representa é recusado inteiro.** Chave com 43
dígitos, NCM fora da forma, data ilegível: a exceção sobe e o documento não é
normalizado. Não há conserto silencioso nem descarte de campo — um documento
representado pela metade entraria no relatório parecendo íntegro.

**O lote registra e continua.** Cada arquivo é lido e fechado antes do seguinte;
falha em um vira `FalhaDeLeitura` — origem, tipo do erro e motivo — e o lote
segue. A única exceção é a falta do sal, que é erro de configuração da instalação
e vale para todos os arquivos. A ordem de processamento sai do nome do arquivo, e
não da listagem do sistema de arquivos, para que dois relatórios do mesmo acervo
possam ser comparados linha a linha.

**Entrada é tratada como não confiável.** DTD e entidades externas estão
desligados no leitor. Sem isso, um documento com `<!DOCTYPE>` faria o processo
abrir arquivo local ou fazer requisição de rede em nome de quem rodou a
auditoria.

### Consequência

Ganhos:

- Trocar de versão de leiaute é baixar outro pacote de XSD e recompilar. Nenhum
  nome de campo do documento fiscal está escrito à mão para ser caçado depois.
- A distinção entre omissão e declaração sobrevive à entrada, que era o único
  ponto onde D002 ainda podia ser perdida sem ninguém ver.
- Não existe caminho pelo qual CNPJ ou CPF cheguem ao domínio, e a afirmação é
  testada por varredura, não por leitura de código.
- Um acervo de dezenas de milhares de notas passa pelo leitor sem caber na
  memória, e o que não pôde ser lido aparece contado e nomeado.

Custos aceitos:

- **O projeto não compila logo depois do clone.** Falta baixar os XSD. É o preço
  de não versionar esquema de terceiro por conta própria; o `LEIAME.md` diz
  exatamente quais arquivos e de onde, e a falha do build aponta o diretório.
- **O caminho do projeto não pode conter acento.** O XJC monta a URI base do
  esquema sem codificar caracteres não-ASCII, e os `xs:include` relativos deixam
  de resolver — em `.../Área de Trabalho/...` a geração falha dizendo que não
  achou `leiauteNFe_v4.00.xsd`. Não é defeito do plugin: acontece igual chamando
  o XJC direto. Espaço no caminho é inofensivo; acento não.
- **NCM de dois dígitos, que o leiaute admite, faz o documento inteiro ser
  recusado**, porque `Ncm` exige oito. Hoje isso vira falha registrada e visível.
  Aceitar esses documentos exige mexer no domínio da Etapa 1, e essa decisão não
  foi tomada aqui.
- **A repetição do CST e da base nos dois tributos é leitura do leiaute atual.**
  Se uma nota técnica passar a declarar CST próprio para a CBS, este é o ponto
  que muda.
- **`indicadorDestinatario` foi mapeado para `dest/indIEDest`** e `crtEmitente`
  para `emit/CRT`. O primeiro é interpretação do nome escolhido na Etapa 1, que
  não registrou de qual campo do leiaute vinha.
- **O leitor de lote captura `RuntimeException` por arquivo.** Erro de
  programação também vira falha registrada, em vez de subir. Fica visível no
  relatório com o nome da exceção, mas não interrompe o lote.
- Os XML sintéticos de teste caem no bloqueio de `*.xml` do `.gitignore` e
  precisam de `git add -f`, um a um. É intencional, e vale a conferida.

### Revisão de 03/09/2026 — os XSD são versionados, e são cinco

Dois pontos do texto acima descreviam o repositório de forma que deixou de
corresponder ao que ele é. O texto original fica como está, porque registra o que
foi decidido na Etapa 4; esta revisão registra o que passou a valer.

**Os XSD são versionados.** O parágrafo acima diz que "não são versionados por
padrão" e que quem clona baixa do Portal. Os arquivos entraram no Git na própria
Etapa 4, no commit `9a9a8be`, e nunca saíram — a afirmação já nascia divergente
do repositório, e assim ficou até a revisão de conformidade das Etapas 0 a 4.

A divergência foi resolvida **ratificando o fato**: os esquemas permanecem
versionados. Não são dado fiscal — são documentos públicos do Portal Nacional da
NF-e —, e o `.gitignore` bloqueia dado de empresa, não esquema de leiaute.
Versioná-los faz `mvn test` funcionar logo depois do clone, o que importa para
quem for avaliar este trabalho, e registra contra qual versão do leiaute ele foi
escrito. A alternativa, tirá-los do Git, foi considerada e recusada.

**São cinco arquivos, e a única fonte do plugin é `nfe_v4.00.xsd`.** O parágrafo
acima fala em seis arquivos e em "os dois esquemas raiz". O sexto,
`procNFe_v4.00.xsd`, era pedido no `LEIAME.md` e listado no `pom.xml` **sem nunca
ter estado no repositório**: o `jaxb2-maven-plugin` o ignorava em silêncio a cada
build, com a linha `Ignored given or default sources`.

Ele não é necessário. Só declara o elemento raiz `nfeProc`; o tipo `TNfeProc`,
que é o que este projeto usa, é um `xs:complexType` do `leiauteNFe_v4.00.xsd`. E
`LeitorDocumentoFiscal` lê o nome do elemento raiz por StAX e desserializa por
tipo declarado, sem depender de `@XmlRootElement` — de modo que documento com
envelope de autorização é lido normalmente sem esse esquema.

Verificado gerando do zero numa cópia do projeto fora do OneDrive, com os cinco
arquivos: as mesmas 52 classes, `TNfeProc` entre elas, e os testes de leitura de
XML verdes, inclusive o que lê documento com envelope `nfeProc`.

**Nota de método, para quem repetir a conferência:** dentro do OneDrive esse
ensaio não é confiável. O sincronizador segura `target/generated-sources/jaxb`, o
`rm -rf` não pega, e o XJC chega a devolver um `[ERROR] null [-1,-1]` que não tem
relação com a configuração. A conclusão acima só foi tirada depois de reproduzir
fora da pasta sincronizada.

---

## D006 — Persistência sem anotação no domínio, e apontamento identificado pelo que aponta

**Etapa:** 5 — Persistência e orquestração
**Status:** Aceita

> **Emendada em parte, duas vezes, na afirmação "a interface de uso é uma linha
> de comando, não uma API REST".** A D009 (Etapa 8) abriu uma saída de leitura
> por HTTP e manteve a escrita na CLI; a D012 (Etapa 11) abriu uma porta de
> escrita, `POST /api/analises`, e manteve fora dela importar catálogo e tratar
> achado. O resto da D006 — domínio sem anotação, apontamento identificado pelo
> que aponta — continua valendo inteiro. O texto abaixo é o que foi decidido na
> Etapa 5, e fica como registro.

### Contexto

A partir desta etapa o sistema guarda o que auditou. Isso trouxe três problemas
que não existiam enquanto tudo era memória.

**O primeiro é o caminho fácil do JPA.** Anotar `Documento`, `ItemDocumento` e
`Achado` com `@Entity` faria a persistência quase desaparecer. Também faria o
domínio depender de `jakarta.persistence`, o que D001 proíbe — e não por
purismo: o modelo do domínio é construído sobre distinções que o mapeamento
automático apaga com naturalidade. `Optional.empty()` vira `null` vira zero;
`BigDecimal` com escala declarada vira `numeric(19,2)`; tipo selado com três
variantes vira uma coluna de texto sem quem saiba reconstruí-lo.

**O segundo é o reprocessamento.** Um acervo fiscal é auditado várias vezes: o
catálogo muda, a regra ganha versão, o lote é reprocessado por segurança. Se o
apontamento for identificado pela linha em que foi gravado, cada rodada cria
apontamento novo, e o trabalho humano de examinar cada um se perde a cada
rodada. Quem audita para de confiar no relatório na segunda semana.

**O terceiro é a tratativa.** Um apontamento examinado por uma pessoa recebe uma
decisão — procede, ou não procede — e uma justificativa. Essa decisão precisa
sobreviver ao reprocessamento. Mas não pode sobreviver a *qualquer* coisa: se a
regra que gerou o apontamento mudar de critério, a justificativa antiga passa a
responder a uma pergunta que não está mais sendo feita.

### Decisão

**Entidades JPA separadas, em `infraestrutura/persistencia`, com mapeadores
escritos à mão.** O domínio continua sem uma única anotação. O preço são cerca
de quinze classes de entidade e quatro mapeadores; o retorno é que cada
conversão entre ausência e nulo é uma linha visível, que alguém escreveu de
propósito e que um teste cobre.

**As migrations criam as tabelas vazias.** Catálogo (as quatro tabelas
normativas da Etapa 2 e suas tabelas filhas), documento, item, apontamento,
evidência, execução e tratativa. Nenhum `INSERT` de dado normativo, em nenhuma
migration, em nenhum ambiente. Colunas de código de origem normativa são `text`,
sem limite: um `varchar(N)` seria uma afirmação sobre quantos caracteres a norma
admite. Colunas monetárias são `numeric` sem precisão declarada, porque
PostgreSQL preserva a escala exata do valor gravado e, para a auditoria, "0" e
"0,00" não são o mesmo registro.

**A identidade do apontamento é o que ele aponta**, não a linha:
`(resumo do item, identificador da regra, versão da regra)`. O resumo do item é
um SHA-256 sobre a chave de acesso, o número do item e todos os campos
declarados, com marca própria para campo ausente e preservando a escala dos
valores. Reprocessar o mesmo lote reencontra a linha, atualiza o conteúdo,
avança a última execução que a viu e mantém a primeira detecção.

**A tratativa usa exatamente essa chave, e não tem chave estrangeira para o
apontamento.** É o que a faz sobreviver ao apontamento ser regravado. E,
**deliberadamente, a versão da regra faz parte da chave**: se a regra mudar de
versão, a tratativa antiga não é encontrada e **o apontamento reabre**. A
tratativa antiga não é apagada — fica no banco, presa à versão em que foi dada.
Mudar o conteúdo do item tem o mesmo efeito, pelo mesmo motivo.

**A execução é sempre nova.** Cada rodada é um fato distinto, com hora, resumo
da entrada, versão do catálogo, versão do conjunto de regras, quantidade de
documentos e de itens, e contagem de apontamentos por severidade e por regra —
essas contagens incluindo zero, para que regra que rodou e nada encontrou não
suma do relatório.

**A interface de uso é uma linha de comando, não uma API REST.** Quatro
comandos: `importar-catalogo`, `auditar`, `listar-achados`, `tratar-achado`. Não
há segundo sistema chamando, não há sessão e não há concorrência entre usuários;
uma API traria autenticação, autorização, versionamento de contrato e superfície
de exposição de documento fiscal real, tudo isso sem nenhum consumidor.

**O catálogo gravado é carregado inteiro para a memória a cada rodada**, e
consultado pelos repositórios em memória da Etapa 2. É neles que mora a
resolução por vigência e a recusa de vigências sobrepostas; reimplementar isso
em SQL criaria duas versões da mesma regra, com risco de divergirem.

**Duas coisas entraram além do que a etapa pedia**, porque sem elas o resto não
funciona: a tabela `carga_catalogo`, que identifica cada importação e dá à
execução o `versaoCatalogo` que ela precisa registrar; e a tabela
`cobertura_catalogo`, que guarda a cobertura declarada por tabela — sem ela o
`ConjuntoRegras` não pode ser montado, e silêncio do catálogo voltaria a se
confundir com tabela não carregada (D004). A cobertura entra por um quinto
arquivo CSV, `cobertura.csv`.

**A tolerância de valor da regra R05 é configuração obrigatória, sem padrão.**
Não é conteúdo normativo — é a escolha de quem audita sobre quanta diferença de
arredondamento não merece apontamento. Escolher por conta própria seria decidir,
em nome do usuário, quantos centavos ficam invisíveis no relatório.
*(Emendado pela D023, 04/10/2026: desde 21/09/2026 o `application.properties`
trazia `0.01` dentro do placeholder, e esta afirmação era falsa sem que nada o
dissesse. Hoje há padrão, declarado numa propriedade própria, e toda execução
grava o valor e a origem, que aparecem em toda saída do resultado.)*

### Consequência

Ganhos:

- Reprocessar o mesmo lote não duplica apontamento e não perde tratativa, e isso
  é provado contra um PostgreSQL de verdade, não contra um dublê.
- O domínio continua testável sem contexto Spring e sem banco: a maior parte dos
  testes do projeto roda em segundos e sem Docker.
- Um relatório antigo continua dizendo contra qual catálogo e qual conjunto de
  regras foi produzido, mesmo depois de novas importações.
- O banco recusa, por restrição de formato, qualquer coisa que não seja resumo
  criptográfico nas colunas de participante. É a segunda barreira contra dado
  pessoal em texto claro, depois da do domínio.

Custos aceitos:

- **Cerca de quinze classes de entidade e quatro mapeadores de código
  repetitivo.** É o preço direto de D001, e é conhecido.
- **O Hibernate roda com `ddl-auto=none`.** A validação automática reclama de
  detalhe de tipo que aqui é deliberado — `numeric` sem precisão —, e falharia na
  subida por um motivo que não é defeito. Quem garante que entidade e migration
  concordam é o teste de integração, que grava e lê todas as tabelas.
- **Sem Docker o teste de integração se desabilita em vez de falhar.** Quem clona
  o projeto para ler não precisa de Docker; quem for mexer na persistência
  precisa, e o teste desabilitado aparece no resumo do Maven — o que também
  significa que uma quebra de persistência passa despercebida em máquina sem
  Docker.
- **Apontamento sobre o documento inteiro ainda não tem gravação.** Nenhuma das
  sete regras produz um — o motor percorre itens —, e a identidade gravada é o
  resumo do item. Se uma regra de documento surgir, o serviço de auditoria falha
  com mensagem explícita em vez de violar restrição do banco em silêncio. Criar
  agora uma identidade para apontamento que não existe seria ponto de extensão
  antecipado.
- **Cada importação de catálogo acumula uma carga inteira no banco.** As antigas
  não são apagadas, para que relatórios produzidos contra elas continuem
  conferíveis. Não há comando para limpá-las.
- **O catálogo inteiro vai para a memória a cada rodada.** É aceitável porque um
  catálogo normativo é pequeno perto de um acervo de notas, e porque a carga
  acontece uma vez por rodada, não uma por documento — mas é um limite real.
- **A origem do lote é lida duas vezes**, uma para o resumo da entrada e outra
  para os documentos. Derivar o resumo dos documentos lidos não serviria: ele
  precisa descrever o que foi apresentado ao sistema, inclusive o que não pôde
  ser lido.
- **Reabrir apontamento a cada mudança de versão de regra vai gerar retrabalho
  visível.** É o comportamento pretendido, e é a parte desta decisão que mais
  provavelmente será questionada. A alternativa — deixar a decisão antiga valer
  para o critério novo — faria uma justificativa silenciar um apontamento que ela
  nunca examinou, e isso é pior.

---

## D007 — O papel de trabalho: identificação no topo, documento sem participante

**Etapa:** 6 — Papel de trabalho em xlsx
**Status:** Aceita

### Contexto

Tudo o que o sistema fez até aqui — ler XML, resolver catálogo por vigência,
aplicar regra, gravar apontamento — só vira auditoria quando uma pessoa consegue
olhar uma linha e decidir se ela procede. A saída é o produto; o resto é meio.

Três problemas apareceram ao construí-la.

**O primeiro é o que a planilha responde meses depois.** Um arquivo encontrado
numa pasta compartilhada em novembro precisa dizer contra qual catálogo e com
que versão de regras foi produzido. Sem isso, um apontamento que hoje não
procede mais — porque a tabela mudou — é indistinguível de um erro do sistema, e
a conversa termina em "não sei, roda de novo".

**O segundo é a tensão entre pseudonimizar e rastrear.** A chave de acesso não é
um identificador neutro: os dígitos dela carregam o CNPJ do emitente. Exportá-la
é exportar o CNPJ com um passo a mais de trabalho para lê-lo — e a planilha é
justamente o artefato que sai da máquina e vai por e-mail. Mas trocar a chave por
um resumo criptográfico, sozinho, deixa a linha sem como chegar à nota: o critério
de pronto da etapa é que o achado leve ao documento *sem consultar o banco*, e um
hash de 64 caracteres não leva a lugar nenhum sem o sistema aberto ao lado.

**O terceiro é que os `NAO_AVALIADO` não existiam.** A Etapa 5 gravou só a
contagem, e o motivo de cada um se perdia no fim da rodada. Sem eles não há aba
de não avaliados nem motivos agrupados — e um lote em que nada pôde ser avaliado
sairia com cara de lote limpo.

### Decisão

**Apache POI, xlsx, três abas: Resumo, Achados e Não avaliados.** A escrita é em
fluxo (`SXSSFWorkbook`), com janela de 500 linhas: um acervo real produz dezenas
de milhares de apontamentos, e a alternativa carrega a planilha inteira na
memória.

**A identificação da execução abre o Resumo, sem nada acima dela.** Execução,
data e hora, versão do catálogo, versão do conjunto de regras, resumo da entrada,
documentos e itens auditados. O leiaute é testado por índice de linha, de modo
que empurrar o bloco para o meio da aba quebra o teste.

**O documento aparece pelo pseudônimo da chave — calculado com o mesmo sal de
instalação de emitente e destinatário — acompanhado de modelo, série, número,
data de emissão e UF.** Nenhum desses cinco é dado de participante: série e
número são a numeração sequencial do próprio emitente, e a data e a UF situam a
operação. Juntos localizam a nota no ERP da empresa; separados do CNPJ, não
identificam ninguém. É o que resolve a tensão do segundo problema sem afrouxar a
regra.

**Os `NAO_AVALIADO` passaram a ser gravados**, numa tabela nova
(`avaliacao_nao_concluida`), com o motivo que a própria regra escreveu.
Diferente do apontamento, **não são deduplicados**: não concluir é fato da
rodada, não do documento. A mesma regra sobre o mesmo item pode não concluir hoje
por falta de tabela no catálogo e concluir amanhã, e o papel de trabalho de cada
execução tem de mostrar o que valia na hora dela.

**Entrou também `achado_da_execucao`**, ligando execução a apontamento. A linha
do apontamento guarda só a primeira e a última execução que o viram; sem o
vínculo, reemitir o papel de trabalho de uma rodada antiga traria os apontamentos
da rodada mais recente, e as contagens do Resumo não bateriam com as linhas da
aba de achados. Uma planilha internamente contraditória é pior que nenhuma.

**Uma linha por achado, com as evidências alinhadas dentro da célula.** Um
apontamento pode ter várias evidências; as colunas de campo, valor encontrado e
valor esperado recebem uma linha cada, na mesma ordem, de modo que a enésima
linha de uma corresponde à enésima das outras. Quem confere quer contar
apontamentos, não evidências.

**Ausência é escrita, nunca deixada em branco.** Campo que não veio no documento
vira `(não informado)`; regra sem valor de referência a opor vira
`(sem referência)`; vigência sem fim vira `(sem fim declarado)`; apontamento sem
decisão vira `ABERTO`. Numa planilha lida meses depois, célula vazia é
indistinguível de célula que ninguém preencheu — e a diferença entre "não veio" e
"veio zero" é o eixo do sistema inteiro desde D002.

**O `exportar` é sempre de uma execução identificada**, nunca "do banco".
Exportar os apontamentos atuais produziria uma planilha sem data de corte,
impossível de reconciliar com outra emitida uma semana depois.

### Consequência

Ganhos:

- Um apontamento na planilha leva ao documento, ao item, ao campo, ao valor
  declarado, ao esperado e ao dispositivo legal sem abrir o sistema.
- Nenhum identificador em texto claro sai na exportação, e isso é verificado
  varrendo o arquivo descompactado — não as células —, de modo que um vazamento
  em nome de aba ou propriedade do documento também apareceria.
- O papel de trabalho de qualquer rodada pode ser reemitido, com os apontamentos
  daquela rodada e a identificação que ela tinha.
- Duas emissões do mesmo papel de trabalho saem iguais: as contagens por regra
  são ordenadas pelo identificador, e não pela ordem de iteração do mapa.

Custos aceitos:

- **Quatro arquivos da Etapa 5 mudaram**, com autorização: `ResultadoDaAuditoria`
  passou a carregar a lista de não concluídas em vez da contagem,
  `ServicoDeAuditoria` a recolhê-las, e `RepositorioDaAuditoria(NoBanco)` a
  gravá-las. `DocumentoEntidade` ganhou acessores de leitura.
- **`avaliacao_nao_concluida` cresce a cada rodada** e não é deduplicada. Num
  acervo grande com catálogo incompleto, ela será a maior tabela do banco. É o
  preço de conseguir reemitir o papel de trabalho de uma rodada antiga.
- **Série e número do documento aparecem na planilha.** Não identificam
  participante, mas identificam a operação. Se o arquivo for tratado como
  público, isso precisa ser reconsiderado — a decisão aqui é que o papel de
  trabalho é documento interno de auditoria.
- **A ordem das contagens por regra é alfabética**, e não a ordem de declaração
  do conjunto. Com identificadores `R01` a `R07` as duas coincidem; com uma regra
  chamada de outro jeito, deixariam de coincidir.
- **O leiaute do Resumo está preso a índices de linha no teste.** Mudar o bloco
  quebra o teste de propósito, mas também obriga a mexer nele em toda alteração
  de leiaute.
- **A largura das colunas é fixa.** O cálculo automático exige ter todas as
  linhas em memória, que é justamente o que a escrita em fluxo evita.
- **`ComandoAuditar`, da Etapa 5, continua imprimindo as contagens por regra na
  ordem de iteração de um `Map` imutável**, que a JVM embaralha a cada execução.
  A correção não foi feita porque está fora do que esta etapa autorizou mexer.

---

## D008 — Acurácia medida por gabarito, com o não avaliado fora das métricas

**Etapa:** 7 — Harness de avaliação
**Status:** Aceita

### Contexto

Até aqui o sistema afirma coisas sobre documentos fiscais. Esta etapa responde a
outra pergunta, que é a contribuição do trabalho: **quanto dessas afirmações se
sustenta**. A única resposta honesta vem de comparar a saída do motor com o
julgamento de uma pessoa sobre os mesmos itens.

Três problemas apareceram, e o segundo é o eixo da etapa.

**O primeiro é de onde tirar a saída do motor.** Ler os apontamentos gravados
pela última auditoria seria o caminho curto e está errado: o banco não guarda
avaliação conforme. A Etapa 5 grava apontamento e a Etapa 6 acrescentou as não
concluídas; a regra que se aplicou por inteiro e nada encontrou não deixa linha
nenhuma. Como são justamente essas que formam os verdadeiros negativos — e, por
diferença, os falsos negativos —, medir pelo banco tornaria metade da matriz de
confusão inobservável.

**O segundo é o que fazer com `NAO_AVALIADO`.** Ele não é acerto e não é erro.
Contá-lo como erro puniria o sistema exatamente por aquilo que o resto do projeto
existe para garantir: dizer "não sei" em vez de dizer "está certo". Contá-lo como
acerto é pior, e é o defeito silencioso que esta etapa tem de tornar impossível —
com ele, um sistema que não avalia nada teria acurácia perfeita, e o número que
sustenta o TCC seria uma mentira aritmeticamente correta.

**O terceiro é o que responder quando não há denominador.** Se o motor não
apontou nada entre as linhas medidas, `VP + FP` é zero e a precisão é uma divisão
por zero. Devolver 1 afirma "de tudo o que o sistema apontou, tudo procedia" a
respeito de um sistema que não apontou nada. Devolver 0 afirma o contrário, e é
igualmente falso.

### Decisão

**O gabarito é um CSV rotulado à mão, com quatro colunas obrigatórias**:
`chave_documento`, `numero_item`, `regra_id` e `rotulo_esperado`. O rótulo é
`ACHADO` ou `CONFORME`, e **`NAO_AVALIADO` é recusado explicitamente**: quem
rotula responde sobre o documento, não sobre o sistema. Rotular "aqui o motor não
vai conseguir julgar" mediria a ferramenta contra a expectativa que já se tem
dela.

**Seis desfechos, quatro dos quais entram na métrica.** `Desfecho` é um enum com
os quatro quadrantes da matriz de confusão mais `NAO_AVALIADO` — o motor não pôde
julgar — e `SEM_AVALIACAO` — o gabarito aponta item que o motor não viu.
`entraNaMetrica()` escreve por extenso a regra que decide o que conta como
medição, para que ela caiba numa linha em vez de ser deduzida das fórmulas.

**Quem garante a regra, porém, é a forma de `ContagemDeAcuracia`** — quatro
campos de matriz de confusão, dois campos à parte, e fórmulas de precisão e
recall que só mencionam os quatro. Nenhum código de produção chama
`entraNaMetrica()`, e é assim de propósito: ele é a especificação, não a
implementação. O que impede os dois de divergirem é
`avaliadosDeveContarExatamenteOsDesfechosQueEntramNaMetrica`, que percorre o enum
inteiro e confronta o que o método afirma com o que o registro conta —
acrescentar um desfecho novo ao enum sem decidir de que lado ele fica quebra esse
teste.

Até 03/09/2026 este parágrafo dizia que `entraNaMetrica()` era "o único lugar do
sistema em que se decide o que conta como medição". A revisão de conformidade das
Etapas 5 a 7 encontrou o método sem nenhum chamador em produção: a decisão
atribuía a garantia a código que ninguém invocava. O teste de amarração entrou
junto com esta correção.

**Precisão e recall se calculam só sobre os quatro quadrantes.** As duas outras
contagens não aparecem em nenhum denominador dessas fórmulas. Elas aparecem em
**cobertura**, que é métrica própria: avaliados sobre o total do gabarito.
Cobertura baixa com precisão alta é resultado legítimo — o sistema acerta o que
julga e julga pouco — e só é legível porque os dois números são reportados lado
a lado.

**Métrica sem denominador é `Metrica.Indefinida`, com o motivo.** O tipo é selado
em duas variantes pelo mesmo raciocínio de `Avaliacao`: um `BigDecimal` obrigaria
alguém, em algum ponto, a escolher um número para o caso sem denominador. Na
saída ela vira `(indefinida)` — nunca campo em branco, que quem tem pressa lê
como zero.

**F1 é calculado como `2VP / (2VP + FP + FN)`**, algebricamente igual à média
harmônica onde as duas são definidas, e **só é definido quando precisão e recall
também são**. A fórmula direta daria zero em casos em que a precisão não existe,
e um resumo não pode afirmar mais que os números que resume.

**O consolidado soma células, não faz média das métricas por regra.** A média
trataria uma regra com três linhas rotuladas igual a uma com duzentas, e
obrigaria a decidir o que fazer com as regras de métrica indefinida — decisão sem
resposta defensável. Somando células, uma regra sem linha no gabarito contribui
com zero e não distorce nada.

**Toda regra do conjunto ganha linha no relatório**, inclusive as que o gabarito
não cita, zeradas e com métricas indefinidas. Omiti-las faria o relatório parecer
completo quando não é.

**O harness roda o motor de novo e não persiste nada.** Não grava execução, não
entra no histórico e não vira papel de trabalho. Medir não é auditar, e uma
opção "não grave" num serviço que grava é a forma mais discreta de um dia gravar
por engano — por isso são dois serviços, e não um com sinalizador.

**Duas recusas duras, ambas para não transformar erro de digitação em conclusão
sobre o acervo:** gabarito que rotula o mesmo endereço duas vezes falha na carga,
como falha uma vigência sobreposta no catálogo (D003); e gabarito que cita
`regra_id` fora do conjunto falha na comparação, listando as regras conhecidas —
sem isso, um `R0X` viraria dezenas de `SEM_AVALIACAO` e o relatório acusaria o
acervo em vez do arquivo.

**O `LeitorCsv` da Etapa 2 mudou de `infraestrutura.catalogo` para
`infraestrutura.csv`, com autorização.** O gabarito usa o mesmo formato, e
duplicar o analisador significaria duas convenções de CSV divergindo com o tempo.
Como o leitor não sabe de que assunto é o arquivo, ele passou a receber
`RecusaDeCsv` — quem o chama diz como nomear a falha. Catálogo malformado
continua produzindo `ImportacaoDeCatalogoInvalida`; o gabarito produz
`GabaritoInvalido`.

**Uma mensagem mudou de texto na generalização**, e vale dizer qual:
`"Nenhuma origem de CSV informada para importação."` virou
`"...para leitura."`. A palavra antiga descrevia o único uso que o leitor tinha
até então; com o gabarito, "importação" passaria a ser falso — gabarito não é
importado, é lido. A mensagem só dispara com `Reader` nulo, que nenhum caminho de
produção produz. Todas as demais mensagens do `LeitorCsv` e do `LinhaCsv` são
idênticas às da Etapa 2, conferidas uma a uma contra o texto anterior.

Entraram também duas mensagens novas, das guardas de `RecusaDeCsv` nula
(`IllegalArgumentException`, alcançável só por erro de programação) e a de
`inteiroObrigatorio`, método novo que o gabarito exige e o catálogo não usava.

### Consequência

Ganhos:

- Precisão, recall e F1 por regra e consolidados, em `BigDecimal` com quatro
  casas, contra um gabarito rotulado à mão — o resultado empírico do trabalho.
- **É impossível `NAO_AVALIADO` inflar métrica.** A separação está no tipo, não
  numa condição: as contagens que não entram na medição são campos distintos de
  `ContagemDeAcuracia`, e nenhuma fórmula de precisão ou recall as menciona.
- O confronto é função pura sobre duas listas, conferível à mão em teste, sem
  leitura de arquivo nem banco no caminho.
- O relatório diz contra qual catálogo e qual versão de regras foi obtido. Um
  número de acurácia sem procedência não sustenta afirmação nenhuma.
- Desalinhamento entre gabarito e acervo aparece com endereço, e não como métrica
  ruim.

Custos aceitos:

- **Repetição deliberada** dos quatro primeiros passos de `ServicoDeAuditoria`.
  Os dois serviços divergem no passo seguinte, e uni-los exigiria o sinalizador
  que a decisão recusa.
- **O harness relê o acervo inteiro a cada medição.** Com trezentos itens é
  irrelevante; com um acervo grande, é uma auditoria completa só para medir.
- **Nove arquivos da Etapa 2 mudaram**, com autorização, e um entrou: `LeitorCsv`
  e `LinhaCsv` mudaram de pacote e ficaram públicos; os quatro importadores e
  `LeitorDeCatalogoEmCsv` passaram a informar como recusam; `ProcedenciaEmCsv`
  trocou de import; `LeitorCsvTest` acompanhou a assinatura; e `RecusaDeCsv` é o
  arquivo novo. `ConfiguracaoDaAuditoria`, da Etapa 5, ganhou dois `@Bean`.
  <br>A contagem dizia "sete" até 03/09/2026, quando a revisão de conformidade
  das Etapas 0 a 4 a conferiu contra o `git diff`.
- **`ComandoAvaliarAcuracia` traduz as próprias recusas em `UsoInvalido`**, em
  vez de `LinhaDeComando` ganhar mais um `catch`. O efeito colateral é útil —
  gabarito malformado sai acompanhado do formato esperado — mas a decisão sobre
  onde tratar erro de arquivo passa a ter dois lugares.
- **A chave de acesso aparece em texto claro no terminal e no comentário do CSV
  de acurácia**, para os endereços que o motor não avaliou. É o mesmo que
  `listar-achados` já faz, e quem mede escreveu o gabarito com essas chaves — mas
  não é o tratamento que a D007 dá ao papel de trabalho, e a diferença é
  deliberada: o relatório de acurácia é insumo de quem opera, não artefato que
  circula.
- **O consolidado é micro, não macro.** Uma regra com muitas linhas rotuladas
  domina o número consolidado. As linhas por regra estão logo acima, mas quem
  citar só o consolidado estará citando uma média ponderada pelo esforço de
  rotulagem.

---

## D009 — HTTP só de leitura, com os três desfechos sobrevivendo à serialização

**Etapa:** 8 — Exposição por HTTP
**Status:** Aceita

> Emenda parcial à D006, que decidiu "a interface de uso é uma linha de comando,
> não uma API REST". A parte da D006 que continua valendo é a maior: **quem
> escreve é a CLI**. O que muda é que passa a existir uma segunda saída de
> leitura, e o argumento que a D006 usou para não ter API — "tudo isso sem nenhum
> consumidor" — deixou de valer, porque agora há.

### Contexto

Sete etapas produziram um sistema que grava: execução, apontamento, evidência,
tratativa, avaliação não concluída. Tudo isso só é legível de dois jeitos —
`listar-achados` num terminal e um `.xlsx` que alguém abre. Esta etapa acrescenta
um terceiro, por HTTP, e **não muda nenhum comportamento**: nada de novo é
gravado, nenhuma regra muda, nenhuma versão de conjunto de regras se move.

Três problemas apareceram, e o primeiro é o eixo da etapa.

**O primeiro é que JSON apaga distinção por omissão.** O sistema inteiro é
construído sobre a diferença entre `ACHADO`, `CONFORME` e `NAO_AVALIADO` — a D002
para separar ausência de zero, a D004 para não deixar silêncio do catálogo virar
conformidade, a D008 para não deixar `NAO_AVALIADO` inflar métrica. Serializar é o
ponto onde essa distinção se perde barato e sem aviso: basta um
`default-property-inclusion=NON_NULL`, escolhido por gosto de quem gera a
resposta, para o campo desaparecer — e quem consome passa a concluir "não está em
achados nem em não avaliados, logo está conforme". A conclusão é falsa e parece
razoável, que é a combinação pior.

Agrava isso um fato do modelo: **o banco não guarda avaliação conforme.** A
Etapa 5 grava apontamento, a Etapa 6 acrescentou as não concluídas, e a regra que
se aplicou por inteiro e nada encontrou não deixa linha nenhuma — é exatamente por
isso que o harness da D008 roda o motor de novo em vez de ler o banco. Então o
terceiro desfecho não está lá para ser lido: ou é calculado e dito como tal, ou o
consumidor o infere sozinho, errado, sem ninguém saber.

**O segundo é que dois campos aceitos no terminal passariam a trafegar na rede.**
A chave de acesso em texto claro, que a D008 admitiu no relatório de acurácia
justamente porque "o relatório de acurácia é insumo de quem opera, não artefato
que circula"; e a justificativa da tratativa, texto livre digitado por pessoa e sem
sanitização, que pode conter CNPJ ou razão social — a página de figuras em
`visualizacao/` já se recusa a ler as duas colunas equivalentes da planilha por
esse motivo.

**O terceiro é como servir HTTP sem mexer no que já está entregue.**
`application.properties` fixa `spring.main.web-application-type=none`, e
`AuditoriaApplication.main` chama `System.exit(SpringApplication.exit(contexto))`
na linha seguinte ao `run(argumentos)` — sem condicional. Os dois são da Etapa 5, e
a regra de etapa manda perguntar antes de mexer.

### Decisão

**Quatro endpoints, todos GET.**

```
GET /api/execucoes                          lista, mais recente primeiro
GET /api/execucoes/{id}                     a execução completa
GET /api/execucoes/{id}/achados             paginado e filtrável
GET /api/execucoes/{id}/nao-avaliados       paginado, com o motivo
```

**Um quinto endpoint foi especificado e removido: `/api/execucoes/{id}/acuracia`.**
Ele não é implementável sem afirmar coisa falsa, e a razão é a D008 estar certa:
acurácia é propriedade de uma **medição contra gabarito**, não de uma execução de
auditoria. Não há de onde ler a medição de uma execução — o harness roda o motor de
novo e **não persiste nada**, por decisão, e `execucao_auditoria` guarda apenas
`hash_entrada`, não o caminho do acervo nem o do gabarito. As três saídas possíveis
eram todas piores que não ter o endpoint: medir sob demanda relendo o acervo inteiro
num GET, e reportar como "a acurácia da execução {id}" um número obtido com o
catálogo *atual*, que pode não ser o daquela rodada; gravar medição, contrariando a
D008 e a proibição de escrita desta etapa; ou responder sempre vazio, um endpoint
que nunca responde nada. **A URL afirmava um vínculo que não existe no modelo.** A
apresentação de acurácia fica para a Etapa 9, lendo o CSV que `avaliar-acuracia` já
produz, por leitura local, fora da API.

**Nenhum endpoint de escrita, e isso não é uma etapa faltando.** Auditar tem efeito
gravado e demora minutos; importar catálogo decide o que o sistema vai afirmar sobre
a norma; tratar achado é ato de uma pessoa identificada. Nenhum dos três cabe atrás
de uma porta sem autenticação, e autenticação está fora do escopo aqui.

**Nenhum caminho novo até o banco.** A API lê pelas portas que as Etapas 5 e 6 já
publicaram — `ConsultaDeExecucoes`, `ConsultaDeAchadosDaExecucao`,
`ConsultaDeNaoAvaliadas`, `ConsultaDeDocumentos`, `PseudonimizadorDeChave` — e
portanto pelos mesmos adaptadores `ConsultaDe...NoBanco`. Zero query nova, zero
repositório novo, zero mapeador novo. O filtro e a paginação acontecem em memória
sobre o que a porta devolve.

**O detalhe da execução passa pelo `MontadorDePapelDeTrabalho`.** O agrupamento dos
motivos de não conclusão já existe lá; reimplementá-lo criaria duas respostas para
"o que esta rodada não conseguiu julgar", com risco de divergirem — o mesmo risco
que a D006 recusou ao manter a resolução de vigência num lugar só. Passando por ele,
**a planilha e o JSON da mesma execução contam a mesma coisa por construção.**

#### Os três desfechos, e o que a resposta nunca faz

- **`naoAvaliado` é campo próprio**, no total e por regra, com os motivos agrupados
  ao lado. Regra que concluiu tudo sai com `0` e `[]` escritos, não sai da lista.
- **Cada linha diz o seu desfecho por extenso**: toda linha de achado carrega
  `"resultado": "ACHADO"` e toda linha de não avaliada carrega `"NAO_AVALIADO"`. A
  distinção é textual, e não posicional — um recorte copiado de uma resposta
  continua dizendo o que é, sem depender de saber em qual campo ele estava.
- **`conforme` é derivado e a conta vai impressa** no campo `derivacao`. A derivação
  é exata, não estimativa: `MotorAuditoria` produz exatamente uma avaliação por par
  (item, regra), então `avaliações = quantidadeItens × regras aplicadas`, e os dois
  outros desfechos estão integralmente gravados. Se a subtração der negativo — banco
  alterado por fora —, o valor é `null` **com o motivo**, e nunca zero: zero seria
  uma afirmação sobre o acervo, e o problema está no banco.
- **Ausência é `null` com um campo irmão explicando**, nunca campo omitido. Vale para
  a chave omitida, a justificativa omitida, a vigência sem fim, o valor em risco não
  calculável e o conforme não derivável. **Está nos construtores dos DTOs**, e não na
  disciplina de quem escreve o montador: `null` sem motivo lança `RespostaInvalida`
  antes de virar resposta, e motivo junto do valor também. É a mesma técnica que
  tornou impossível apontamento sem evidência na Etapa 1.
- `spring.jackson.default-property-inclusion=always` é **load-bearing**, não gosto.
  Está comentado como tal no arquivo de perfil.

**Valor monetário é serializado como texto.** `"valorEmRisco": "0.00"`, e não
`0.00`. A escala declarada tem significado — para a auditoria, `0` e `0,00` são
registros diferentes do mesmo número (D002), e essa distinção é preservada desde a
leitura do XML. Um número JSON sobrevive ao servidor e morre no cliente:
`JSON.parse` de `0.00` devolve `0`. Perder no último metro o que se preservou em sete
etapas seria um desperdício silencioso.

#### Privacidade: o trade-off do terminal não se transporta

**Os dois campos sensíveis são opt-in, desligados por padrão**, por configuração da
instalação e **não** por query param:

| propriedade | padrão |
|---|---|
| `auditoria.api.expor-chave-de-acesso` | `false` |
| `auditoria.api.expor-justificativa` | `false` |

No terminal, o alcance de um `listar-achados` é a sessão de quem digitou. Uma
resposta HTTP é outra coisa: é copiável, cacheável por intermediário, gravável em
log de acesso, e chega a um cliente que ninguém auditou. Fosse query param, quem
consome escolheria quanto dado pessoal receber, e a primeira integração escreveria
`?chave=true` porque foi conveniente — quem decide é quem instala. E o padrão é
restritivo **na ausência da propriedade**, não só quando ela diz `false`: é o
inverso do que a Etapa 5 fez com o sal e a tolerância, que param o sistema quando
não configurados, porque ali a falta de escolha é ambígua e aqui não é. Esquecer de
configurar não vaza nada.

O documento é sempre identificado pelo **pseudônimo da chave**, calculado com o mesmo
sal de instalação do papel de trabalho (D007), de modo que duas linhas do mesmo
documento se reconhecem entre respostas. **Modelo, série, número, data de emissão e
UF continuam ligados**, pela mesma leitura da D007: não são dado de participante, e
juntos localizam a nota no sistema da empresa sem o CNPJ aparecer. A ressalva da D007
também vale — eles identificam a operação —, e é parte do motivo de a API escutar só
em localhost.

#### Como o processo serve HTTP sem alterar a Etapa 5

**Nenhum arquivo da Etapa 5 mudou.** Dois arquivos novos:

- `application-api.properties`, do perfil `api`, que sobrepõe
  `web-application-type=servlet` e fixa `server.address=127.0.0.1`. Propriedade de
  perfil tem precedência sobre a base, e a base fica intocada — **sem o perfil, nada
  muda: nenhum servidor sobe.**
- `ComandoServir`, que **bloqueia** até o contexto fechar.

O `System.exit` não deixa de ser chamado: **ele nunca é alcançado**, porque o
bloqueio acontece dentro da linha anterior. `SpringApplication.run()` inicia o
servidor durante `refreshContext()` e só depois executa os `CommandLineRunner`, na
própria thread `main`. Enquanto o comando não devolve, `run()` não retornou. No
Ctrl+C, o gancho de encerramento do Spring fecha o contexto, um
`@EventListener(ContextClosedEvent)` libera a espera, o comando devolve e o
`System.exit` original roda normalmente.

**O perfil não pode entrar como argumento.** `Argumentos.de` exige
`<comando> [--opcao=valor]`: `--spring.profiles.active=api` na frente vira "comando
desconhecido", e atrás vira "o comando servir não conhece a opção". Ele entra por
onde não passa por `main(String[])`:

```
java -Dspring.profiles.active=api -jar auditoria-ibs-cbs-<versao>.jar servir
SPRING_PROFILES_ACTIVE=api java -jar auditoria-ibs-cbs-<versao>.jar servir
```

**Sem o perfil, `servir` recusa em vez de pendurar o processo.** Bloquear sem
servidor deixaria o processo parado para sempre atendendo nada — não dá erro e não
funciona, o pior desfecho possível. A recusa traz a invocação exata e sai com
código 2.

#### Fronteira, e escopo fechado

`SpringWebNaoVazaDaApiTest` varre o código de produção fora de
`infraestrutura/api` e falha se alguém referenciar `org.springframework.web` ou
`com.fasterxml.jackson` — por linha de `import` **e** por nome qualificado no corpo
do arquivo, as duas varreduras desde o início. O Jackson é o que mais importa
vigiar: a D001 proíbe anotação de serialização no domínio desde a Etapa 0, e até a
Etapa 7 essa proibição era barata porque o Jackson não estava no classpath. Agora
está.

Fora do escopo, deliberadamente: **sem autenticação** (e é por isso que o bind é
localhost), **sem CORS**, **sem HTTPS**, **sem multiusuário**.

> **Emenda da Etapa 11 (D012):** "só de leitura" deixou de ser verdade. Passou a
> existir **uma** porta de escrita, `POST /api/analises`, porque a pergunta que a
> ferramenta passou a responder não existe sem a nota entrar. A emenda é estreita
> e o resto desta decisão continua valendo: importar catálogo e tratar achado
> seguem fora, pelos motivos escritos acima, e o bind continua em `127.0.0.1` —
> agora segurando também uma porta de escrita. As quatro exclusões do parágrafo
> anterior seguem valendo sem alteração.

### Consequência

Ganhos:

- Os três desfechos sobrevivem à serialização, e a garantia é estrutural: campo
  omitido não compila, `null` sem motivo não constrói, e os testes de contrato
  conferem o texto da resposta em vez de desserializá-la — desserializar mediria o
  Jackson contra si mesmo, porque um campo omitido voltaria a existir como nulo.
- O conforme deixou de ser um número que cada consumidor calcularia à sua maneira, e
  passou a vir calculado com a conta ao lado.
- A API e o papel de trabalho da mesma execução não podem discordar: as contagens de
  não avaliadas saem do mesmo montador.
- Nenhum identificador em texto claro sai por padrão, e a exposição é decisão de
  quem instala, verificável numa linha de configuração.
- A CLI continua idêntica no que faz e nos códigos de saída que devolve, e há teste
  que afirma isso com o comando novo presente no mapa.

Custos aceitos:

- **A listagem de comandos da CLI ganhou uma linha** (`servir`). É a única mudança
  observável na CLI sem o perfil `api`, e está afirmada em teste para ninguém a
  descobrir por acidente. A alternativa — esconder o comando atrás de `@Profile` —
  faria `servir` sem perfil responder "comando desconhecido" em vez da recusa que
  ensina a invocação certa.
- **`spring-boot-starter-web` no classpath é, por si, uma mudança.** Sob
  `web-application-type=none` nenhum servidor sobe, mas a autoconfiguração do Jackson
  passa a valer e a subida fica marginalmente mais lenta.
- **Filtro e paginação em memória.** O endpoint de achados carrega os apontamentos da
  execução para servir uma página deles. É o preço de não abrir um segundo caminho de
  acesso a dados, e num acervo grande é trabalho real.
- **O detalhe da execução carrega os apontamentos** só para servir contagens que o
  recibo já tem, porque passa pelo montador do papel de trabalho. Mesma troca.
- **A derivação do conforme depende de uma invariante do motor** — uma avaliação por
  par (item, regra). Se algum dia uma regra passar a não avaliar certos itens, o
  número muda de significado sem ninguém mexer nesta camada. A mitigação é a conta ir
  impressa: quem confere vê `quantidadeItens 340 - achado 4 - naoAvaliado 12` e
  percebe. *(Emenda de 27/09/2026: na tela de execuções da visão técnica a conta
  passou para trás de um botão "i", que abre por clique, toque ou teclado. Ver a
  revisão de 27/09/2026 na D010.)*
- **Sem autenticação, o bind em localhost é o único controle de acesso.** Trocar
  `server.address` por `0.0.0.0` sem antes resolver autenticação publica documento
  fiscal de uma empresa real para a rede inteira. O arquivo de perfil diz isso no
  comentário da propriedade.
- **Duas propriedades de configuração a mais para quem instala**, e nenhuma delas tem
  efeito visível quando esquecida — o que é a intenção, mas também significa que
  quem esperava a chave na resposta vai procurar por que ela não veio. O motivo vem
  escrito no próprio campo irmão, que é onde a pessoa está olhando.

---

## D010 — Interface de leitura em HTML puro, com o nível de agrupamento escrito na resposta

**Etapa:** 9 — Interface web para quem lê os achados
**Status:** Aceita

> Não emenda nenhuma decisão anterior. A D009 abriu uma saída de leitura por HTTP
> e disse que quem escreve continua sendo a CLI; esta etapa acrescenta um
> consumidor para aquela saída, e não altera nada do que ela decidiu.
>
> **Emenda da Etapa 11 (D012):** estas seis telas deixaram de ser a porta de
> entrada e passaram a ser a **visão técnica**, em `tecnica.html`. Elas continuam
> inteiras, com os módulos JavaScript desta etapa sem uma linha alterada, e estão
> a um clique da interface de conferência. Tudo o que esta decisão registra sobre
> elas continua valendo; o que mudou foi o endereço e o rótulo.

### Contexto

A API da Etapa 8 responde JSON, e JSON não é o produto: o produto é um relatório
de apontamentos que um contador vai ler para decidir o que corrigir. Até aqui a
única forma legível era o papel de trabalho em `.xlsx`, que circula bem mas exige
um passo de exportação e não tem navegação.

O usuário desta tela não quer explorar dados. Ele recebe um lote auditado e
precisa saber o que corrigir, em que ordem, e com que fundamento. A referência é
a própria planilha: a tela precisa ser pelo menos tão útil quanto ela, ou não se
justifica.

### Decisão

**HTML, CSS e JavaScript puros, servidos de `src/main/resources/static/`.** Sem
framework, sem empacotador, sem npm, sem CDN. O navegador resolve os `import`
entre módulos sozinho, e o Spring serve os arquivos como estão. Nenhum arquivo
Java foi criado ou alterado nesta etapa — nem das Etapas 0 a 7, nem da 8.

O roteamento é por fragmento (`#/execucao/<id>/achados`), e não por caminho, para
que não exista regra de reescrita no servidor: o navegador só pede `/`.

**Cinco telas, mais um detalhamento:** execuções, panorama, achados, detalhe do
achado, acurácia, e a lista linha a linha dos não avaliados — esta última porque
o panorama mostra o agregado e clicar em "118 não avaliados" precisa levar a
algum lugar. Sem ela, `NAO_AVALIADO` seria categoria que a interface conta mas
não deixa examinar.

#### O nível de agrupamento é campo da resposta, com rótulo escrito

Agrupar achados repetidos por (NCM + cClassTrib + regra) é o que transforma
oitocentas linhas idênticas em um cadastro a corrigir. Mas **NCM e cClassTrib não
são campos do achado**: a API os expõe apenas dentro de `evidencias`, e só quando
a regra que apontou de fato os examinou. R03 e R04 trazem os dois; R01, R02 e R07
trazem só `cClassTrib`; R06 traz só `ncm`; R05, que confere valor de tributo, não
traz nenhum dos dois — as evidências dela são de base e de valor.

A saída correta não é preencher o que falta, nem buscar o campo fora da
evidência: é **degradar a chave e dizer que degradou**. Cada grupo carrega:

- `nivel`, um código estável — `NCM_E_CLASSTRIB`, `SOMENTE_CLASSTRIB`,
  `SOMENTE_NCM`, `SOMENTE_REGRA`;
- `rotuloDoNivel`, a frase inteira — "agrupado somente por regra (sem NCM e sem
  cClassTrib)".

É o mesmo par que `ErroExposto` usa em `erro`/`mensagem`. Quem olha um grupo sabe
por qual chave ele foi formado sem consultar documentação, e nunca confunde um
grupo de NCM + cClassTrib com um grupo que só podia ser de regra.

#### Ausência é escrita, e o motivo é observado

Componente ausente vem `null` **com o campo irmão dizendo por quê** —
`ncm`/`motivoDoNcmAusente` —, a mesma convenção de
`valorEmRisco`/`motivoDoValorAusente` e `chaveAcesso`/`motivoDaChaveOmitida` da
Etapa 8.

E o motivo é **observado, não decorado**: a página não sabe que "R05 não examina
NCM". Ela conta quantas evidências daquela regra, naquela execução, trazem o
campo, e escreve o que contou — "nenhuma das 2 ocorrência(s) de R05 nesta
execução traz o campo `ncm` na evidência". Uma tabela de regras e campos escrita
na interface seria conhecimento normativo em código de apresentação, e
envelheceria sozinha na primeira regra nova.

#### A ordem padrão é valor em risco, e a soma parcial se declara

Três ocorrências somando muito vêm antes de oitocentas somando pouco. A soma é
feita em `BigInt` sobre os dígitos, preservando a escala declarada: passar por
`Number` desfaria no cliente a distinção entre `0` e `0,00` que o servidor tomou
o cuidado de preservar (D002, D009).

Grupo em que parte das ocorrências não tem valor calculável soma o que dá e
**declara que a soma é parcial**. Grupo em que nenhuma tem valor calculável não
vai para o fim da lista ordenada por dinheiro — sai numa seção própria, com
título próprio. O rodapé de uma lista ordenada por valor é exatamente onde se lê
"isto não vale nada", e ali não há valor zero: há ausência de valor.

#### Ordenar e agrupar exigem carregar tudo

A API pagina e ordena por severidade, que é a ordem certa para ela: é
determinística. Ordenar por valor e agrupar por cadastro são operações globais —
o grupo de maior valor pode ter uma ocorrência na primeira página e duas na
última. Fazer isso sobre um recorte daria um resultado que parece certo e está
errado. Então a tela busca todas as páginas, de 500 em 500, mostrando o progresso.

#### A listagem de execuções faz uma segunda chamada por linha

`GET /api/execucoes` traz a identificação e o total de apontamentos, mas não traz
não avaliados nem conformes. Uma lista montada só com isso escreveria "43
apontamentos" e pararia — e uma execução com 4 apontamentos e 7 não avaliados
apareceria como lote quase limpo, que é o defeito que esta interface existe para
não ter. Cada linha busca o próprio detalhe. Enquanto não chega, escreve
"lendo…"; se falhar, escreve que não foi possível ler. Em nenhum dos dois casos
mostra zero.

#### A planilha é oferecida como comando, não como link

A regra de apresentação pedia o link do `.xlsx` em toda tela de execução. **Não
existe endpoint que sirva o arquivo**: a API tem quatro GET e nenhum deles produz
arquivo; a planilha é do `exportar`, na CLI. Então o botão abre a invocação
exata, com o identificador da execução já preenchido, pronta para copiar. Um
botão que parecesse baixar e devolvesse 404 seria pior que a instrução honesta.

Criar o quinto endpoint foi considerado e recusado nesta etapa: alargaria a
fronteira HTTP da D009 sem necessidade, e a etapa se resolve sem isso.

#### A tela de acurácia não fala com a API

A D009 removeu `/api/execucoes/{id}/acuracia` de propósito — acurácia é
propriedade de uma medição contra gabarito, não de uma execução de auditoria, e
o harness não persiste nada (D008). A fonte aqui é o próprio CSV do
`avaliar-acuracia`, escolhido num campo de arquivo e lido dentro do navegador.
Nenhum byte sai da máquina.

O cabeçalho de comentários desse CSV lista, **em texto claro**, as chaves de
acesso dos endereços que o motor não avaliou, e os dígitos intermediários da
chave carregam o CNPJ do emitente (D005, D007). A página **conta essas linhas e
nunca as escreve** — a mesma recusa de `visualizacao/resultados.html`.

#### Cor nunca é a única codificação

Todo desfecho tem símbolo além da cor; toda severidade tem triângulos; toda faixa
de gráfico tem hachura própria (45°, 135°, pontilhado) e o número escrito ao
lado. Métrica indefinida sai como `(indefinida)` e **não desenha barra** — barra
de altura zero seria dizer "zero", e zero é um número.

Nada que vem da API ou do CSV é interpretado como marcação: todo texto entra por
`textContent`.

### Consequências

- **A interface não tem teste automatizado neste repositório.** Não há
  dependência de navegador no `pom.xml`, e acrescentar uma para uma etapa de
  apresentação custaria mais do que resolve. A verificação foi feita por sonda em
  Node contra um DOM mínimo, com dado fictício, conferindo nas duas direções: as
  seis telas desenham, as regras de apresentação aparecem no texto produzido, e
  sabotar o discriminador dos dois zeros derruba a conferência. A sonda não está
  versionada.
- **A tela de achados carrega o conjunto inteiro antes de desenhar.** Em execução
  grande isso é uma espera visível, com progresso escrito. É o preço de ordenar
  por valor e agrupar por cadastro sem mentir, e a alternativa — ordenar dentro
  da página — não é mais barata, é errada.
- **Um achado de R05 nunca se agrupa por cadastro.** A regra não expõe NCM nem
  cClassTrib, então o grupo é da regra inteira. Isso está escrito no próprio
  grupo, mas é uma limitação real: para R05, a tela não responde "qual cadastro
  corrigir".
- **Levar `ncm` e `codigoClassificacaoTributaria` para `AchadoExposto` resolveria
  o ponto acima em seis das sete regras**, ao custo de alterar DTO, montador e
  testes de contrato da Etapa 8. Foi considerado e recusado nesta etapa. Se a
  limitação incomodar, é a decisão a revisitar.
- **A página depende de módulos ES.** Abrir `index.html` por duplo clique não
  funciona: `file://` bloqueia `import` entre módulos, e não haveria API do outro
  lado de qualquer forma. Ela é servida pelo próprio sistema, com o perfil `api`.

### Revisão de 27/09/2026 — a tela de execuções em visual de painel

O usuário pediu para limpar a tela de execuções da visão técnica, que tinha
muita coisa disputando a atenção. O texto acima fica como está; esta revisão
registra o que passou a valer **só nessa tela**. Vale também para a D009, no
ponto da conta do conforme.

A mudança fica em `js/telas/execucoes.js` e num arquivo novo, `css/execucoes.css`.
O estilo só vale quando o `<body>` tem a classe `na-execucoes`, que `js/app.js`
liga nessa rota. O panorama e as demais telas não mudaram, e a figura hachurada
de `svg.js` continua inteira, em uso no panorama.

**Três pedidos batiam em regras escritas, e o usuário decidiu cada um:**

- **Barra plana no lugar da hachurada.** Cor continua não sendo a única
  codificação, mas o recurso mudou: cada fatia leva a marca de forma do desfecho
  (● ▲ ■), a legenda repete marca e número, e valor zero ou não derivável segue
  sem fatia e escrito na legenda. A barra só com cor, sem nenhuma marca, foi
  oferecida e recusada. As severidades viraram pílulas **sem triângulos**, porque
  o nome da severidade vai escrito dentro delas e é ele que codifica.
- **A conta do conforme foi para trás de um botão "i".** Isso emenda a D009,
  que dizia que ela sai "impressa ao lado". Nesta tela, a conta e a explicação do
  não avaliado abrem por clique, toque ou teclado (Esc fecha), e não só ao passar
  o mouse. **A exceção é o conforme não derivável:** ali o motivo substitui o
  número e continua à vista, porque ausência é escrita e nunca fica escondida.
  Nas outras telas a conta continua impressa.
- **O hash de entrada sai abreviado com reticências.** O valor inteiro continua
  no texto do documento: ele aparece ao passar o mouse (`title`), por inteiro
  quando o campo recebe foco pelo teclado, e sai completo ao selecionar e copiar.
  O subtítulo deixou de dizer que a identificação aparece "inteira".

Contraste medido para AA e anotado no cabeçalho de `css/execucoes.css`.

**Verificação:** feita por sonda em Node contra um DOM mínimo, com dado fictício.
A sonda não é versionada. Foram 18 conferências passando, e três sabotagens
acusadas: fatia sem marca de forma, motivo do não derivável escondido, e "não
derivável" virando zero na legenda. **Não foi aberta em navegador.**

### Revisão de 27/09/2026, mais tarde — o panorama no mesmo visual, e as abas

O mesmo tratamento foi estendido ao panorama, a pedido do usuário, com as mesmas
três decisões acima: barra plana com marca de forma, conta atrás do botão "i", e
hash abreviado com o valor inteiro no texto. Para as duas telas usarem as mesmas
peças, a grade, a barra e o botão "i" foram para `js/comum.js` e para um módulo
novo, `js/painel.js`. `css/execucoes.css` virou `css/painel.css`, e a classe do
`<body>` passou de `na-execucoes` para `visual-painel`, ligada nas duas rotas.

- **O gráfico por regra e a tabela viraram uma tabela só**, com as colunas
  regra, apontamentos, não avaliados, conformes e situação. **A coluna "leitura"
  saiu**, porque repetia a situação. A distinção entre os dois zeros, que a
  regra 2 de `INTERFACE-WEB.md` mostrava em quatro lugares, passa a aparecer em
  três, nenhum dependente de cor:
  - o número de não avaliados, sempre escrito, inclusive zero;
  - a situação por extenso, com marca própria — ● "Avaliou tudo" e ▲ "Nem tudo
    foi avaliado". O pedido era só a bolinha; o triângulo no segundo caso segue
    a marca do não avaliado;
  - os motivos, na seção logo abaixo.
  Saiu o gráfico hachurado.
- **As explicações longas foram para botões "i"**: o total de avaliações com a
  conta, a nota de que nenhum desfecho se deduz dos outros, e o parágrafo sobre
  os motivos. O pedido era tooltip só ao passar o mouse; usei o mesmo botão da
  tela de execuções, que também abre por clique, toque e teclado. O conforme não
  derivável continua com o motivo à vista.
- **Os botões Panorama / Achados / Não avaliados viraram abas**, com
  `aria-current` na atual. Como `navegacaoDaExecucao` é compartilhada, as abas
  aparecem iguais também em achados, no detalhe do achado e em não avaliados.
- `barraDeDesfechos` e `barrasPorRegra`, em `svg.js`, ficaram sem chamador.
  Foram mantidas, como na revisão da Etapa 1 com métodos sem uso.

**Verificação:** feita por sonda em Node contra um DOM mínimo, com dado fictício.
O panorama teve 19 conferências passando e a tela de execuções continuou com 18.
Cinco sabotagens foram acusadas:
- situação igual para os dois zeros;
- zero de não avaliado escrito em branco;
- motivo do conforme não derivável escondido;
- todas as abas marcadas como atuais;
- fatia sem marca de forma, em `painel.js`, que as duas telas usam.

**Não foi aberta em navegador.**

### Revisão de 27/09/2026, por último — a acurácia com área de envio, e a página sem rodapé

A pedido do usuário:

- **Em `tecnica.html` saíram o texto abaixo da barra (`.sub-topo`) e o rodapé.**
  O `index.html` continua com os dois.
- **O cabeçalho da acurácia ficou curto.** O texto técnico foi para um botão
  "?", que é o mesmo botão "i" das outras telas: medir não é auditar, nenhum
  byte sai da máquina, e as chaves de acesso do cabeçalho do CSV são contadas e
  nunca escritas.
  - A frase pedida terminava em "de forma segura". Troquei por "o arquivo não é
    enviado a lugar nenhum", que é o que a tela de fato garante.
- **O campo de arquivo virou uma área de envio**: um `<label>` ligado ao campo
  por `for`, com o campo **escondido só da vista, e não com `display:none`** —
  `display:none` o tiraria da ordem de foco, pelo mesmo motivo registrado em
  `conferencia.css` na Etapa 13. O estado da leitura é anunciado com
  `aria-live`.
- **O botão usa `#1a365d`**, como nas outras telas deste visual, e não
  `#2563eb`.

**Verificação:** feita por sonda em Node contra um DOM mínimo, com 12
conferências passando. Três sabotagens acusadas:
- `label` sem ligação com o campo;
- campo com `display:none`;
- a chave de acesso deixando de ser contada.

A terceira passou despercebida na primeira rodada. Com essa sabotagem, a linha
da chave continua descartada mais adiante no código, então nada vazava; o que
mudava era a contagem, e a sonda não a conferia. A conferência da contagem foi
acrescentada. **Não foi aberta em navegador.**

---

## D011 — Sal resolvido em cascata, e troca de sal recusada em vez de silenciosa

**Etapa:** 10 — Resolução do sal e guarda de troca
**Status:** Aceita

> Emenda parcial à D005, que decidiu que o sal "não tem valor padrão e não pode
> ter". A parte que continua valendo é a maior: **não há sal fixo em código**. O
> que muda é que a ausência de configuração deixou de parar o sistema e passou a
> produzir um sal sorteado por instalação, gravado fora do repositório.

### Contexto

O sal exigia variável de ambiente declarada à mão. No Windows isso trava a
subida com frequência, e numa apresentação é ponto de falha: quem esqueceu a
variável descobre no momento em que o terminal está projetado.

Ao mexer nisso apareceu um problema maior, e que já existia. Trocar o sal não
dava erro nenhum. O sistema subia, processava e produzia relatório de aparência
normal.

### O que a troca de sal quebra, e o que ela não quebra

**Não quebra tratativa.** A chave da tratativa é
`(HashDoItem, regraId, regraVersao)`, e `HashDoItem` **não leva sal** — é
identidade reproduzível entre instalações, não sigilo (D006). Decisão humana
registrada sobrevive à troca e se reaplica sozinha no reprocessamento.

**Quebra a coerência interna do acervo.** `documento.emitente_pseudonimizado` e
`documento.destinatario_pseudonimizado` saíram do sal anterior. Com sal novo, o
mesmo participante passa a existir sob dois pseudônimos no mesmo acervo, e o
pseudônimo do documento deixa de bater com o que já saiu em planilha e em API.

**E esse é o defeito pior, não o mais brando.** Tratativa reaberta o usuário vê:
o apontamento volta à lista. Participante duplicado não aparece em lugar nenhum —
nenhuma contagem muda, nenhum relatório acusa, e o acervo fica incoerente com
aparência normal. É a mesma família do `R04: 0 apontamentos` que não se
distinguia de `R04: não avaliado`: falso negativo silencioso.

### Decisão

**Resolução em cascata**, em `infraestrutura/sal/ResolvedorDeSal`:

1. propriedade `auditoria.pseudonimizacao.sal`;
2. variável de ambiente `AUDITORIA_PSEUDONIMIZACAO_SAL`;
3. arquivo local — `%APPDATA%/auditoria-ibs-cbs/sal` no Windows,
   `$XDG_CONFIG_HOME` ou `~/.config/auditoria-ibs-cbs/sal` fora dele;
4. sal novo de 256 bits de `SecureRandom`, gravado no arquivo de (3), anunciado
   em nível visível.

As duas primeiras são as que a D005 já lia, **na mesma precedência relativa**.
Inverter (1) e (2) faria uma instalação que declara as duas passar a usar a
outra e, com o guarda ligado, passar a recusar a subida. Correto, mas gratuito.

**Não foi criado nome novo de variável.** `AUDITORIA_SAL` chegou a ser cogitado e
foi descartado: não existe base instalada para compatibilizar, e apelido com
precedência e aviso de conflito é complexidade para um problema que não existe.

**Por que gerar não contradiz a D005.** O que a D005 proíbe é sal *fixo em
código*: esse é público, está no jar, e torna o pseudônimo reversível por força
bruta. Um sal sorteado por instalação e gravado fora do repositório não tem
nenhuma dessas propriedades. O que a geração troca é outra coisa — antes,
esquecer de configurar parava o sistema; agora, esquecer produz um segredo que só
existe naquela máquina. Daí o anúncio dizer onde o arquivo ficou.

#### O guarda recusa por padrão, e permite por exceção

O banco guarda a **impressão digital** do sal (SHA-256 com rótulo de separação de
domínio), nunca o sal. Na subida, `GuardaDeSalNaSubida` compara. Havendo
documento gravado e impressão digital divergente, **recusa**.

A lista é de quem **passa**, não de quem é barrado: `diagnosticar-sal` e
`recomecar-do-zero`. Um comando novo nasce protegido, e atravessar o guarda exige
acrescentá-lo à lista de propósito. Lista de exclusão deixaria o comando
esquecido rodando contra pseudônimo incoerente — e lista de exclusão é
exatamente o tipo de coisa que ninguém revisita.

Os dois que passam são os que existem para resolver essa situação. Barrá-los
deixaria a pessoa com um sistema que não sobe e nenhuma ferramenta para entender
por quê.

**"O banco tem dados" é medido por `documento`**, e não por qualquer linha em
qualquer tabela: é a única tabela com valor salgado. Catálogo importado não tem
sal nenhum, e travar por causa dele seria recusar por um motivo inexistente.

#### Onde o guarda se encaixa sem tocar a Etapa 5

`GuardaDeSalNaSubida` e `LinhaDeComando` são os dois `CommandLineRunner` do
sistema. O guarda declara `@Order(HIGHEST_PRECEDENCE)` e aquele não declara
ordem nenhuma, então o Spring executa o guarda primeiro. Foi o que permitiu
interceptar **todo** comando sem alterar uma linha de `LinhaDeComando`.

#### A recusa não tem rastro de pilha

A explicação sai pela `Saida`, e a exceção é lançada com rastro vazio. A recusa é
uma decisão, não uma queda: um rastro de pilha atrás da explicação a faria
parecer defeito do sistema justamente na hora em que a pessoa precisa acreditar
no que leu. A pilha também não informaria nada — só existe um lugar que lança.

#### `recomecar-do-zero` preserva tratativas

Apaga documentos, itens, execuções e apontamentos. **Não apaga tratativas**, pela
razão de sempre: a chave delas não leva sal, continuam válidas, e se reaplicam no
reprocessamento. Apagá-las destruiria trabalho de auditoria que o problema do sal
nunca tocou. Catálogo também fica, por não ter sal. A confirmação é
`--confirmo=sim`, e não uma marca solta, porque o interpretador de argumentos
trata opção sem valor como opção em branco.

### Consequências

- **`dominio/` não foi alterado em comportamento.** A única mudança sob
  `dominio/` é um parágrafo de Javadoc em `HashDoItem`, acrescentado por pedido
  explícito, registrando que a ausência de sal ali é o que preserva a tratativa
  quando o sal muda. Estava implícito e convidava ao "conserto" errado: quem
  encontrasse um resumo sem sal ao lado de um pseudônimo com sal tenderia a
  acrescentar sal, amarrando toda tratativa registrada ao segredo da instalação.
- **`SalDeInstalacao` não mudou de comportamento**, só de documentação — com
  cláusula de emenda, preservando o texto original. O que mudou é quem o fornece.
- **Um arquivo de produção de etapa anterior foi alterado:**
  `ConfiguracaoDaAuditoria`, cujo `@Bean` do sal passa a delegar ao resolvedor e
  a publicar também `SalResolvido`.
- **Quem perder o arquivo de sal perde a continuidade dos pseudônimos.** Antes,
  o sal estava onde a pessoa o tivesse posto; agora pode estar num arquivo que
  ela não sabe que existe. O anúncio da criação diz o caminho, e
  `diagnosticar-sal` repete quando perguntado.
- **A impressão digital vai para o backup junto com o banco.** Quem obtiver o
  backup pode tentar força bruta sobre ela. Só funciona se o sal for adivinhável,
  e o piso é 32 caracteres, com o gerado vindo de 256 bits.
- **Instalação anterior a esta etapa adota a impressão digital sem verificar
  nada**, porque não há com o que comparar. A ressalva fica gravada em
  `adotada_de_acervo_existente` e o diagnóstico a repete, para não afirmar uma
  conferência que não houve. A partir daí, a proteção vale.
- **O guarda não roda em `@SpringBootTest`**, que não executa `CommandLineRunner`.
  Isso mantém a suíte existente intacta e obrigou o teste do guarda a construí-lo
  explicitamente — o que, de resto, é como se testa.

---

## D012 — Conferência de enquadramento: quatro estados, uma porta de escrita, e a carga que produziu o resultado

**Etapa:** 11 — Conferência de enquadramento de IBS/CBS
**Status:** Aceita

> Emenda a **D006** e **D009** na parte da porta de escrita, e a **D010** na parte
> da interface. O que cada uma decidiu continua registrado onde está; o que muda
> está dito aqui, e nenhuma delas foi reescrita.
>
> **Não emenda, e não pode emendar:** D002 (ausência é um estado), D003 (toda
> consulta normativa se resolve na data do documento), D004 (silêncio do catálogo
> só vira apontamento dentro da cobertura declarada) e D008 (o não avaliado fora
> das métricas). Esta etapa se apoia nas quatro.

### Contexto

As dez primeiras etapas produziram uma ferramenta que responde bem a uma
pergunta: *há incoerência nos campos de IBS/CBS deste documento?* A saída é um
relatório de apontamentos, o vocabulário é o de quem audita — achado, severidade,
tratativa — e a interface lista execuções.

A pergunta que quem trabalha no fiscal faz é outra: *que tratamento se aplica aos
produtos desta nota, e o que o XML declara é coerente com ele?* É a mesma
máquina respondendo, mas a resposta precisa sair organizada por produto, com a
base normativa ao lado, e num vocabulário que não afirme mais do que o sistema
sabe.

Há um risco embutido nessa mudança, e ele é o motivo de esta decisão existir. A
tradução de "nenhuma regra apontou" para uma frase amigável tende a produzir
**"Conferido"** — e o sistema não conferiu nada. Ele aplicou as regras
cadastradas, sobre os campos que elas alcançam, e nenhuma encontrou violação. São
afirmações diferentes, e a segunda é a verdadeira. É exatamente o defeito que a
R04 já corrigiu uma vez, do lado do motor, e que reapareceria do lado da tela.

O segundo risco é a medição. A acurácia deste trabalho foi medida contra o motor
como ele está. Qualquer mudança em regra, severidade ou desfecho invalidaria os
números escritos no TCC.

### Decisão

#### O motor não foi tocado, e isso é a primeira parte da decisão

**Nada sob `dominio/` mudou.** Nem regra, nem objeto de valor, nem catálogo, nem
os três desfechos `ACHADO | CONFORME | NAO_AVALIADO`. As sete regras continuam
sendo as sete regras, com a mesma lógica e as mesmas severidades, e
`ConjuntoRegras.VERSAO_PADRAO` continua `2026.1`.

Tudo o que esta etapa faz é **leitura e apresentação** do que aquele motor
produziu. Os números de precisão, recall e F1 medidos na Etapa 7 continuam
correspondendo ao código.

#### Quatro estados visíveis, traduzidos num lugar só

| Estado | O que ele afirma |
|---|---|
| `POSSIVEL_DIVERGENCIA` | o declarado não corresponde ao que a regra aponta |
| `REQUER_CONFERENCIA` | a situação merece leitura de quem responde pelo fiscal |
| `NAO_FOI_POSSIVEL_CONCLUIR` | faltou dado no documento ou na base carregada |
| `SEM_DIVERGENCIA_IDENTIFICADA` | as regras cadastradas foram aplicadas e nenhuma encontrou violação |

A tradução acontece em `TraducaoDeDesfecho`, e em nenhum outro lugar. Ela é
chaveada por **severidade, não por identificador de regra**: não existe a string
`"R04"` em parte alguma da camada de conferência. Uma regra nova de severidade
informativa cai em `REQUER_CONFERENCIA` sem que ninguém precise lembrar disso, e
uma severidade nova quebra a compilação, porque o `switch` é exaustivo e não tem
`default`.

**A ordem de declaração de `EstadoDeConferencia` é a precedência**, como em
`Severidade`. Um produto com uma divergência e três verificações sem conclusão
aparece como divergência — a mais forte prevalece — e é justamente por isso que
existe o terceiro número descrito adiante.

**`SEM_DIVERGENCIA_IDENTIFICADA` nunca é escrito como "Conferido"**, e o Javadoc
do enum diz isso em voz alta. Há teste afirmando que nenhum rótulo e nenhuma
explicação dos quatro estados contém "conferido", "conferida" ou "verificado".

#### As quatro regras de apresentação são conteúdo

1. **"Não foi possível concluir" tem o mesmo peso visual dos outros três.**
   Nunca cinza, nunca atrás de um clique, nunca fora do resumo.
2. **O resumo mostra sempre os quatro números, inclusive os zeros.**
3. **Cor nunca é a única codificação**: todo estado sai com marca de forma e com
   o rótulo por extenso, os dois vindos do servidor.
4. **Em lugar nenhum um produto não avaliado é somado aos sem divergência.**

A quarta não é sustentada por disciplina. `ContagemDeEstados` recusa um mapa que
não traga os quatro estados, e **deliberadamente não oferece nenhum método que
combine** `SEM_DIVERGENCIA_IDENTIFICADA` com `NAO_FOI_POSSIVEL_CONCLUIR`: não há
onde escrever a soma. Um teste por reflexão percorre os métodos públicos sem
argumento do pacote e falha se algum devolver o valor da soma proibida. No CSS,
a mesma disciplina: uma única regra `.estado-celula` para as quatro células, sem
seletor que reduza peso, e nenhuma classe de "total" ou "consolidado".

#### O terceiro número, que a precedência esconderia

`ResumoDaConferencia` traz três coisas, e não duas: os produtos por situação, as
verificações por estado, e **quantos produtos têm ao menos uma verificação sem
conclusão**. O terceiro existe porque a precedência faz a pendência sumir do
nível do produto. Sem ele, uma nota com dez divergências e quarenta pendências
apareceria com "0 não foi possível concluir".

#### `POST /api/analises` — a emenda à D009, e o quanto ela é estreita

A D009 decidiu HTTP somente para leitura. Passa a existir **uma** porta de
escrita: analisar. O motivo é que a pergunta que a ferramenta passou a responder
não existe sem a nota entrar, e mandar quem trabalha no fiscal digitar
`java -jar` não é ter produto.

**Importar catálogo e tratar achado continuam fora**, pelos mesmos motivos da
D009: o primeiro decide o que o sistema afirma sobre a norma, o segundo é ato de
uma pessoa identificada, e não há autenticação aqui. O bind continua em
`127.0.0.1`, e agora ele segura uma porta de escrita.

**Não há caminho paralelo de processamento.** `ServicoDeAnalise` não lê XML, não
normaliza, não pseudonimiza, não monta contexto normativo, não aplica regra e não
grava apontamento: ele constrói um `ServicoDeAuditoria` — o mesmo objeto que o
comando `auditar` usa — e acrescenta duas coisas que a CLI não precisava.

A primeira é **leitura isolada**. O registro de falhas da Etapa 4 é um objeto só,
vivo enquanto o processo vive; isso bastava para um comando que roda e encerra, e
deixaria a segunda análise herdar os arquivos ilegíveis da primeira. Cada análise
recebe o seu.

A segunda é o **acervo**: `item_da_execucao` (V7) registra o que a análise leu,
inclusive os itens que não produziram nada. Sem ela, a lista de produtos teria de
ser derivada dos apontamentos — e uma nota inteiramente sem divergência sumiria
do resultado da própria análise que a leu.

**Arquivo ilegível não é nota sem divergência.** Ele fica em
`falha_de_leitura_da_execucao` (V7) e é contado à parte, nunca somado a nenhum
dos quatro estados. O resumo do lote mostra os quatro números **mais** a contagem
de ilegíveis, e `ResumoDaConferencia` nem sequer tem campo onde eles caberiam.
*(Emendado pela D018, 04/10/2026: até essa data, só a análise da interface
gravava a tabela; o `auditar` imprimia as falhas e não as gravava, e a tabela
vazia era lida como zero. Hoje as duas gravam, e execução sem a leitura
registrada responde "não registrado", nunca zero.)*

#### O conforme é derivado, e a derivação é subtração

O banco não grava avaliação conforme (D009). Mas grava apontamento e pendência
endereçados por documento, item e regra, e a execução registra **todas** as
regras aplicadas. Como o motor produz exatamente uma avaliação por par
(item, regra), as regras da execução menos as que apontaram menos as que ficaram
pendentes **são** as que concluíram sem violação. Não é estimativa.

O que a derivação não recupera é a **versão da regra**, que só existe nas linhas
gravadas. Inferi-la da versão do conjunto seria afirmação que dependeria de o
código de hoje ainda montar aquele conjunto. Daí `VersaoDaRegra`, selado em
`Registrada | NaoRegistrada(motivo)` — a forma de `ValorEmRisco`.

#### O tratamento é resolvido contra a carga que a execução registrou

Este é o ponto em que a etapa quase repetiu um erro que o projeto já tinha
recusado. Reabrir uma análise de janeiro e resolver o tratamento dos produtos com
o catálogo importado em setembro mostraria, ao lado de apontamentos produzidos
com uma tabela, uma fundamentação que não os produziu — e a pessoa leria as duas
coisas como se fossem a mesma. É o que a D009 recusou ao tirar acurácia de dentro
da execução, e o que a D003 recusa ao exigir data.

Por isso existe `ProvedorDeCatalogoPorVersao`, ao lado de `ProvedorDeCatalogo`:
aquele carrega a carga mais recente, que é o certo para auditar; este carrega a
carga que a execução registrou, que é o certo para reabrir o resultado dela. A
montagem é a mesma classe, de propósito — fossem dois carregadores, o tratamento
exibido poderia divergir do que a auditoria usou por diferença de implementação.

**As duas coordenadas andam juntas**: a carga *e* a data de emissão do documento.
Uma sem a outra não identifica nada, e as duas vão escritas na resposta.

Carga que não está mais gravada não cai na mais recente: a resposta diz que não
foi possível determinar o tratamento, com o motivo, e mantém a mesma forma —
para a tela não ter um desenho especial para o fracasso.

#### IBS e CBS em blocos separados, e as parcelas não se somam

A tela apresenta CBS e IBS em quadros próprios porque podem divergir: um produto
pode estar coerente num e não no outro, e uma linha única esconderia exatamente o
caso que interessa conferir.

Dentro do bloco do IBS, as parcelas estadual e municipal aparecem como duas
linhas, cada uma com a própria vigência e a própria fonte. **O sistema não as
soma.** Somar produziria um percentual que nenhuma linha da carga declara —
número inventado, ainda que por aritmética.

Os três tributos aparecem **sempre**, inclusive os que a carga não alcança, que
vêm com o motivo. Tributo omitido é lido como tributo que não incide, e
`TratamentoIdentificado` recusa no construtor uma lista que não traga os três.

#### A comparação não emite um segundo veredito

O quadro de declarado e indicado põe os dois lados um do lado do outro e para aí.
Ele não escreve "confere" nem "não confere".

Quem julga são as sete regras, e o julgamento delas já está na situação do
produto. Um veredito próprio ali seria um oitavo juízo, mais fraco que os outros
sete: ignoraria a tolerância de valor, a cobertura declarada da carga e as
condições que cada regra examina. Na prática diria "diferente" onde a regra
concluiu sem violação — e a pessoa veria a interface contradizendo o motor sem
ter como saber qual dos dois está certo.

O que a comparação afirma é só sobre **presença**: quando um dos lados não
existe, ela diz qual e por quê. Isso é fato sobre o documento e sobre a carga,
não sobre a norma. Um teste por reflexão recusa qualquer componente novo da linha
cujo nome soe a veredito.

#### "Por que este resultado" sai do que foi gravado

Não existe neste projeto uma tabela dizendo "R03 significa isto". A explicação de
cada passo vem de três procedências, e o tipo é selado para que uma quarta não
possa ser acrescentada sem que se decida o que ela explica:

- **apontamento** — as evidências que ele gravou: campo examinado, valor
  encontrado, valor oposto, e de onde o valor veio;
- **pendência** — o motivo que a própria regra escreveu ao desistir;
- **conforme** — a conta que o derivou, dita como conta.

A consequência é que a explicação acompanha a regra sem ninguém manter nada em
dia. Um texto decorado ficaria para trás em silêncio, dizendo sobre a regra de
ontem o que a de hoje não faz mais.

#### A tela do lote é outra tela

Não é a da nota repetida n vezes. Quem trabalha no fiscal corrige **cadastro**,
não nota: um NCM classificado errado aparece em quatrocentas notas e continua
sendo um erro de parametrização. Uma lista de quatrocentas linhas iguais mostra o
tamanho do estrago e esconde a causa.

O agrupamento é por **(NCM + cClassTrib + situação)**. A situação entra na chave
porque o mesmo par pode ter desfechos diferentes em notas diferentes, e misturá-los
obrigaria a tela a escolher um deles.

**A ordem padrão é por valor dos produtos envolvidos, decrescente, e ela mede
exposição — não gravidade.** Um grupo caro com erro trivial de preenchimento sobe
acima de um grupo barato que perdeu um benefício. Isso é aceitável como padrão, e
quem lê precisa saber: o significado da ordem viaja na resposta, e há ordenação
alternativa por contagem, para a pergunta oposta.

**O rótulo do valor nunca é abreviado.** "Valor dos produtos envolvidos" é o
tamanho da operação, não o do erro — abreviado para "valor", numa coluna ao lado
de "possível divergência", seria lido como prejuízo. O rótulo vem do servidor.

**O nível do agrupamento vai escrito**, como na D010. A diferença é que aqui NCM
e `cClassTrib` vêm de `item_documento`, o que o documento declarou, e não das
evidências — então o nível depende do documento, e não de qual regra apontou.
Produto que não declarou nenhum dos dois forma grupo próprio, com o nível dizendo
isso; ele não é descartado nem jogado num "outros" mudo, e o construtor de
`AgrupamentoDaAnalise` recusa um agrupamento cujos grupos não somem o total.

#### A descrição do produto: a primeira coluna de texto livre do emitente

A tela de detalhe mostra a descrição da nota ao lado da descrição que o catálogo
dá ao NCM. Divergência entre as duas costuma indicar classificação errada —
informação que nenhuma das duas dá sozinha, e que nenhuma regra produz, porque
nenhuma delas examina texto.

`xProd` não era lido em lugar nenhum do sistema. Como `ItemDocumento` é objeto de
valor sob `dominio/` e não muda, e como **nenhuma regra examina a descrição**,
ela não entra no domínio: é registrada pelo leitor, por análise, e gravada na
linha de `item_da_execucao` que já identifica aquela leitura.

**A chave é a identidade que o sistema já usa.** Quem grava e quem lê calculam o
endereço com a mesma função — `HashDoItem.de(chave, item)` — sobre as mesmas
entradas. Uma tabela à parte chaveada pelo hash seria **incorreta**, e não apenas
arriscada: a descrição não entra no resumo do item, então o mesmo hash pode
legitimamente acompanhar duas descrições diferentes, e a tabela teria de escolher
uma e apagar a outra.

**O acumulador tem escopo de análise, nunca de processo.** Ele nasce dentro da
fábrica de leitura e morre com a análise. Acúmulo aqui não tem sintoma
observável — o endereço continua devolvendo a descrição certa — e o que ele faria
é segurar, pelo tempo que o servidor ficar de pé, o texto livre de toda nota que
passou. Como o defeito é de forma, o guarda também é: um teste varre o
código-fonte e falha se o acumulador virar campo de componente do Spring.

**Isto emenda o que o V2 prometia.** A afirmação de que o esquema não tem coluna
de dado pessoal em texto claro continua valendo para identificador de
participante: não há CNPJ, CPF, razão social nem endereço em coluna nenhuma. Mas
`xProd` é digitado por quem emitiu, em escala, sem revisão, e na prática traz nome
de cliente e referência de pedido. A contrapartida são três controles: corrida de
44 dígitos é substituída por marcador antes de gravar; a restrição da V8 é a
barreira de última instância; e a exposição por HTTP é **opt-in**, com a
exportação nunca a levando.

**O que nenhum dos três alcança é prosa.** "P/ OBRA FULANO" passa por qualquer
verificação de forma. Está declarado assim, e não disfarçado atrás de uma
checagem que daria impressão de cobrir o que não cobre.

#### A procedência da carga é derivada do dado, e por tabela

A alternativa recusada era uma propriedade de instalação do tipo
`auditoria.demonstracao=true`. Ela é promessa de quem configurou: quem esquecesse
de ligá-la veria dado fictício apresentado como norma vigente — que é exatamente
o modo de falha que a marcação existe para evitar.

Transportar o cabeçalho `# ATENCAO: DADOS INTEIRAMENTE FICTICIOS` até a carga
também não serve, e o segundo motivo é decisivo: `LeitorCsv` descarta linhas de
comentário antes de qualquer chamador vê-las, e — mesmo que não descartasse —
**ausência de prosa é indistinguível de prosa apagada**.

A decisão é uma coluna `natureza` obrigatória em cada um dos quatro CSV de dados,
com valor `FICTICIO` ou `NORMATIVO`. Linha sem ela recusa o arquivo inteiro, e
duas naturezas no mesmo arquivo também — um arquivo tem uma procedência só.
`cobertura.csv` não a tem: ele declara período e fonte, não conteúdo, e a de
alíquota não teria onde ser declarada, porque alíquota não tem linha de cobertura.
*(Revertido pela D021, 04/10/2026, a pedido do usuário: a fonte que o
`cobertura.csv` declara é citada como fundamento dos apontamentos, e fonte
fictícia saía sob "Catálogo normativo". O `cobertura.csv` passou a declarar
natureza, com a mesma regra dos outros arquivos.)*

**Guardada por tabela, e não por carga**, por causa do caso misto: alguém carregar
um anexo real e o resto fictício. Um sinalizador único teria de escolher entre
chamar a carga de real ou de fictícia, e as duas respostas estariam erradas. Por
tabela, a derivação diz "parcialmente fictício" e **lista quais tabelas**.

**Ausência de linha não é "normativo".** Carga importada antes desta etapa
aparece como procedência não declarada, e a tela pede reimportação. Supor que dado
de origem desconhecida é norma vigente seria a afirmação mais cara que este
sistema poderia fazer por engano.

A faixa vai em **toda** resposta de resultado, e não só onde o catálogo é
exibido: a situação de um produto foi produzida contra aquela carga, e as telas
que as pessoas mais olham são justamente as que não mostram uma linha da tabela.

#### A interface: a conferência na porta, a visão técnica preservada

`index.html` passou a ser a conferência. A interface da Etapa 9 continua inteira,
com os módulos dela **intocados**, em `tecnica.html`, e está a um clique — no
menu e no fim da tela de análises anteriores. O rótulo "visão técnica" é
explícito porque as duas contam a mesma coisa com vocabulários diferentes.

A tela de acurácia fica no menu principal: é o resultado do TCC, e procurar por
ela na frente de uma banca seria constrangedor.

A tela inicial tem **uma ação**, e não abre com lista: quem chega quer conferir
uma nota, e uma lista de execuções no lugar do campo de envio obriga a pessoa a
procurar o botão antes de fazer a única coisa que veio fazer.

A base tributária **abre pedindo a data**, sem valor padrão. É o caso de uso que a
D003 previu — "o que vale hoje" como caso de uso próprio, com data explícita — e
um padrão silencioso de "hoje" traria de volta o problema que ela fechou. Ela
consulta por NCM e por `cClassTrib` e não lista as tabelas inteiras, porque os
repositórios do domínio expõem busca pontual e alargá-los sairia da restrição
desta etapa.

O **aviso de uso** vem do servidor e é exigido no construtor de toda resposta de
resultado. Se morasse no JavaScript, a tela nova que esquecesse não quebraria
nada — ficaria só sem aviso, que é o modo de falha mais provável e o menos
visível.

### Consequência

- **A acurácia medida na Etapa 7 continua válida.** Nada sob `dominio/` mudou em
  comportamento; as duas únicas diferenças ali são parágrafos de Javadoc de
  etapas anteriores.
- **Existe uma porta de escrita por HTTP, sem autenticação, atrás de
  `127.0.0.1`.** Era leitura pura; não é mais. Quem expuser a porta expõe a
  capacidade de gravar execução e documento.
- **`item_da_execucao` cresce com cada análise**, uma linha por item lido. É o
  preço de conseguir listar os produtos de uma nota inteiramente sem divergência.
- **Execução feita pela CLI não tem acervo**, e por isso não tem produtos a
  listar. A tela diz isso e oferece a visão técnica, em vez de mostrar "0
  produtos" ao lado de "300 itens lidos".
- **Os CSV de catálogo existentes param de importar** até ganharem a coluna
  `natureza`. O acréscimo é mecânico, e a alternativa — coluna opcional — teria o
  mesmo buraco do cabeçalho de comentário.
- **Cargas gravadas antes desta etapa ficam com procedência não declarada.** Não
  se supõe normativo; a tela pede reimportação.
- **A descrição do produto é opt-in e sai desligada.** Consequência prática: a
  comparação lado a lado com a descrição do NCM só aparece inteira com
  `auditoria.api.expor-descricao-do-produto` ligada. Foi o regime pedido, e o
  custo é este.
- **Arquivos de etapas anteriores foram alterados, com autorização prévia e
  cláusula de emenda em cada um.** `NormalizadorDocumento` e `LeitorLote` (Etapa
  4) ganharam o registro de descrições, sem sobrecarga e sem valor padrão;
  `ItemDocumentoEntidade`, `ItemDocumentoJpa`, `MapeadorDeDocumento`,
  `CargaCatalogoJpa`, `ProvedorDeCatalogoNoBanco`,
  `RepositorioDeCargaDeCatalogoNoBanco`, `CatalogoParaAuditoria` e
  `CargaDeCatalogo` (Etapas 2 e 5) acompanharam; os quatro importadores de CSV e
  `LeitorDeCatalogoEmCsv` (Etapa 2) passaram a devolver a procedência junto dos
  registros; `ConfiguracaoDaApi`, `TratadorDeErrosDaApi`, `Parametros` e
  `ComandoServir` (Etapa 8) acompanharam a porta de escrita.
- **`index.html` deixou de ser a interface técnica.** O conteúdo dela está em
  `tecnica.html`, com os módulos JavaScript da Etapa 9 sem uma linha alterada.
- **Não há teste automatizado de navegador**, pela mesma razão da D010: não há
  dependência de navegador no `pom.xml`. A verificação foi por sonda em Node
  contra um DOM mínimo, nas duas direções — e a primeira rodada da sonda deixou
  passar duas sabotagens, por furos dela que foram corrigidos e registrados.
- **Os quatro desfechos têm teste de ponta a ponta**, com uma nota só e quatro
  cargas: é a carga que produz a diferença, e trocar o documento provaria menos.

### Revisão de 12/09/2026 — a regra sai pelo nome, com o código ao lado

As duas interfaces escreviam a regra só pelo código. "R06" diz a quem programa
qual classe rodou e não diz nada a quem lê o resultado, e foi o que o usuário
pediu para mudar. O texto acima fica como está; esta revisão registra o que
passou a valer, e vale também para a D010.

**Onde o nome mora.** Numa tabela em `infraestrutura/api/NomeDaRegra`, chaveada
pelas constantes `ID` das próprias regras. Três lugares foram considerados:

- *No domínio*, como método de `RegraAuditoria`. Recusado: mexeria nas sete
  regras e em `dominio/regras`, que todas as revisões registram como intocado
  desde a Etapa 3. E a API teria de instanciar regras com uma cobertura fictícia
  só para ler o nome, porque as regras exigem cobertura no construtor.
- *No JavaScript*. Recusado: a D010 afirma que a página não tem tabela nenhuma
  sobre as regras, e duas interfaces decorariam a mesma tabela.
- *Na infraestrutura da API*. Escolhido, com decisão explícita do usuário.

**Por que uma tabela por identificador não repete o erro que a
`TraducaoDeDesfecho` evitou.** Aquela classe recusou tabela por identificador
porque "quem esquecesse de editá-la descobriria pelo relatório". Para decidir
estado isso seria fatal; para dar nome, a tabela é inevitável, e o esquecimento
não chega à tela: `NomeDaRegraTest` compara a tabela com
`ConjuntoRegras.padrao(...).identificadores()` nas duas direções. Regra nova sem
nome quebra o build, e o mesmo acontece com nome de regra removida.

**O nome não é afirmação sobre a norma.** Cada um resume a pergunta que a regra
faz, tirada da primeira linha do Javadoc dela, sem código, percentual, vínculo ou
data. A seção 5 do CLAUDE.md segue intacta.

**O código continua na resposta e na tela.** `regraId` não mudou de nome nem de
posição; o nome entra como campo novo ao lado. Na tela, o código sai menor, junto
do nome, porque é ele que a CLI aceita em `--regra`, que a planilha escreve e que
o gabarito usa. O usuário escolheu isso em vez de esconder o código.

**Ausência é o par da D009.** Código que a tabela não conhece — uma execução
gravada com um conjunto que o código de hoje não monta — vem com `regraNome` nulo
e `motivoDoNomeDaRegraAusente` preenchido, e o construtor recusa os dois vazios
ou os dois preenchidos. A tela escreve o motivo, nunca o código repetido no lugar
do nome. O nome é o do código de hoje; enquanto as sete regras estiverem em
`1.0.0`, isso coincide com o que rodou em qualquer execução gravada, e a versão
da regra continua exposta ao lado para quando deixar de coincidir.

**Fora desta revisão:** a tela de acurácia continua só com o código. Ela lê o CSV
do `avaliar-acuracia` sem falar com a API, e o CSV não traz nome. Pôr o nome ali
exigiria mudar o escritor da Etapa 7 ou decorar a tabela no JavaScript.

**Arquivos de etapas anteriores alterados, com autorização prévia:**

- Etapa 8: `AchadoExposto`, `NaoAvaliadaExposta`, `RespostaDaExecucao.PorRegra` e
  `MontadorDeRespostas`; nos testes, `RepresentacaoNuncaOmiteTest` (quatro
  chamadas de construtor ganharam os dois argumentos) e `MontadorDeRespostasTest`
  (um teste novo).
- Etapa 9: `css/componentes.css`, `js/formato.js`, `js/svg.js`,
  `js/agrupamento.js`, `js/telas/panorama.js`, `js/telas/achados.js`,
  `js/telas/achado.js` e `js/telas/naoavaliados.js`. A afirmação da consequência
  acima, "com os módulos JavaScript da Etapa 9 sem uma linha alterada", valeu até
  esta data.
- Etapa 11: `PassoExposto`, `ProdutoExposto.VerificacaoExposta`,
  `MontadorDaConferenciaExposta`, `css/conferencia.css` e
  `js/conferencia/telas/produto.js`.
- Novos: `NomeDaRegra` e `NomeDaRegraTest`.

Nada sob `dominio/` mudou, e a acurácia da Etapa 7 continua correspondendo ao
código.

**Verificação.** `mvn test`: 884 testes, nenhuma falha, **83 pulados** — os de
Testcontainers, porque o Docker não estava de pé na máquina. Entre os pulados
estão `ApiDeLeituraTest`, `AnaliseDePontaAPontaTest` e
`QuatroCenariosDeConferenciaTest`, que exercitam o JSON por HTTP de verdade. Eles
procuram trechos como `"regraId":"R04"`, que o campo novo não quebra, mas isso é
leitura, não execução: **não foram rodados nesta revisão**.

A interface foi verificada por sonda em Node contra um DOM mínimo, com dado
fictício, nas duas direções: 44 conferências passando nas cinco telas afetadas
(seis cenários, com a de achados agrupada e solta), e
cinco sabotagens acusadas, cada uma conferida antes como tendo de fato alterado o
arquivo. O detalhe está no `INTERFACE-WEB.md`. A sonda não é versionada.

### Revisão de 14/09/2026 — o CSV de classificação por tributo, e a data em dd/mm/aaaa

Uma carga montada a partir de planilha não importava por dois motivos de forma:
as datas vinham em `dd/mm/aaaa`, e a planilha trazia `dispositivoLegal`, redução
e `fonteNormativa` separados para CBS e para IBS, enquanto o importador esperava
uma coluna só de cada. As duas mudanças foram pedidas pelo usuário. O texto acima
fica como está; esta revisão registra o que passou a valer.

**A data.** `LinhaCsv` passou a aceitar `dd/mm/aaaa` ao lado de `aaaa-mm-dd`. A
presença da barra escolhe o formato, em vez de uma tentativa depois da outra, para
que a recusa venha do formato que o arquivo usou. O modo é **estrito**: no modo
padrão do `java.time`, `31/02/1900` viraria 28/02 sem aviso, e uma vigência
deslocada resolve o documento contra o registro errado. Ano com quatro dígitos,
dia e mês com dois. **Risco aceito:** um arquivo em `mm/dd/aaaa` seria lido
trocado sem aviso sempre que o dia fosse até 12. A fonte usada é brasileira, e o
usuário autorizou nesses termos. Só as vigências do catálogo passam por esse
caminho: o gabarito da D008 não tem data, e o parâmetro `data` da API continua
exigindo `aaaa-mm-dd`, por `Parametros`, que não mudou.

**O CSV por tributo.** Dois caminhos foram postos ao usuário:

- *Separar CBS e IBS no modelo.* Recusado: mudaria `ClassificacaoTributaria`, em
  `dominio/catalogo`, e a `RegraTratamentoDeAnexoNaoAproveitado`, que lê a
  redução. A regra mudaria de versão, o que reabre tratativa (D006) e desfaz a
  correspondência entre o código e a acurácia medida na Etapa 7. O ganho seria de
  exibição: nenhuma regra confere o valor da redução.
- *Juntar o par no importador, sem mexer no domínio.* Escolhido pelo usuário.

A forma por tributo vale para os três campos, com as colunas
`dispositivoLegal_cbs`/`_ibs`, `reducao_cbs`/`_ibs` e
`fonteNormativa_cbs`/`_ibs`. A escolha entre as duas formas é campo a campo, e o
importador **junta sem escolher**:

- valores iguais viram um valor só;
- texto diferente é guardado inteiro e rotulado, `CBS: … | IBS: …`. As colunas no
  banco são `text`, sem limite a estourar;
- **redução diferente recusa a linha**, com os dois valores na mensagem. Número
  não se rotula, e ficar com um dos dois seria decidir sobre a norma dentro do
  código. A comparação é do valor como foi escrito, casas decimais incluídas —
  `99,9` e `99,90` são recusados, porque guardar um deles seria escolher a escala
  que a tela exibe. Vírgula e ponto se equivalem, como no resto do CSV;
- célula em branco num dos lados é recusada como seria na coluna única;
- redução em branco nos dois lados continua sendo **redução não declarada**, que
  não é redução zero. A regra acima lê `0` como redução declarada;
- o cabeçalho que traz as duas formas para o mesmo campo, ou só metade do par, é
  recusado: com as duas, não há como saber qual vale.

A forma de coluna única continua aceita sem nenhuma diferença, e os testes dela
não mudaram.

**Custos.** Uma única linha com redução divergente recusa o arquivo inteiro,
porque a importação é tudo ou nada, até o usuário decidir o valor no CSV. A tela
continua mostrando uma redução só para os dois tributos, como antes. E o texto
rotulado aparece como está na evidência e na conferência.

**Fora desta revisão, por decisão do usuário:** o arquivo salvo em Windows-1252
continua recusado com a `MalformedInputException` crua, sem nome de arquivo nem
linha; o arquivo gravado com BOM continua recusado — conferido com a primeira
linha sendo comentário, que deixa de ser reconhecido e vira cabeçalho; e o nome
de cada arquivo continua exigido por extenso.

**Também não mudou, e não foi pedido:** `natureza` é comparada com maiúsculas e
minúsculas, e `Normativo` é recusado.

**Arquivos de etapas anteriores alterados, com autorização prévia:**

- Etapas 2 e 7: `infraestrutura/csv/LinhaCsv`.
- Etapa 2: `ImportadorClassificacaoTributariaCsv` e `ProcedenciaEmCsv`. Este ganhou
  uma variante que recebe a leitura da fonte. A original passou a delegar a ela,
  mantendo a ordem de leitura: vigências antes da fonte.
- Nos testes, `LeitorCsvTest`: **um literal**. O exemplo de data fora do formato
  era `01/01/1900`, que passou a ser formato aceito, e virou `1900/01/01`. As
  asserções não mudaram.
- Novos: `DataEmDiaMesAnoNoCsvTest` e
  `ImportadorClassificacaoTributariaPorTributoCsvTest`.
- `README.md`, na seção do `importar-catalogo`.

Cada arquivo de produção alterado carrega cláusula de emenda com o que valia
antes. Nada sob `dominio/` mudou, e a acurácia da Etapa 7 continua correspondendo
ao código.

**Verificação.** `mvn test`: **904 testes, nenhuma falha, nenhum pulado** — com o
Docker de pé, de modo que desta vez os de Testcontainers e os de API por HTTP
rodaram. São os 884 da revisão anterior mais os 20 novos. As seis classes de CSV e
de catálogo, rodadas antes à parte, somaram 70 testes verdes, entre elas as
classes da forma de coluna única, sem alteração.

Na direção contrária, três sabotagens, cada uma conferida antes como tendo de fato
alterado o arquivo, e cada uma acusada:

- tirar o modo estrito da data derruba
  `deveRecusarDiaQueNaoExisteEmVezDeAjustarParaOFimDoMes`;
- fazer a redução divergente ficar com a CBS derruba três testes — divergência,
  um lado em branco e casas decimais diferentes;
- fazer o texto divergente ficar com a CBS derruba os dois testes de rótulo.

Os dois arquivos foram restaurados por cópia e conferidos byte a byte com a versão
final antes da suíte completa.

Sonda com o leitor compilado sobre a carga do usuário, lida sem alteração: ela
para numa linha com fonte e dispositivo em branco do lado do IBS e redução
divergente — recusa esperada por esta revisão. Numa cópia sem essa linha, para em
`natureza` escrita em caixa mista. Com as duas coisas contornadas na cópia, as
cinco tabelas são lidas. A gravação no banco pelo `importar-catalogo` **não foi
exercitada** sobre essa carga. A sonda não é versionada.

### Revisão de 27/09/2026 — a tela de envio enxugada, e a conferência sem rodapé

A pedido do usuário, a tela inicial da conferência (`conferencia/telas/enviar.js`
e o arquivo novo `css/envio.css`, ligado pela classe `na-envio` no `<body>`)
foi enxugada:

- **Subtítulo curto.** As três explicações viraram uma legenda só: "Formatos
  aceitos: .xml (NF-e/NFC-e) ou arquivos compactados em .zip. (Atenção: formato
  .rar não suportado.)". Saíram:
  - o motivo técnico da recusa do `.rar`. O servidor continua recusando o
    `.rar` com a mensagem clara da Etapa 11;
  - a frase de que arquivo ilegível do pacote não interrompe os outros. A tela
    de resultado continua contando os ilegíveis à parte.
- **A área de arrastar continua.** O botão virou chamada principal, e os dois
  atalhos viraram botões discretos, com ícone.
- **Em `index.html` saíram o texto abaixo da barra e o rodapé**, como já tinha
  sido feito na visão técnica. A tela de login perdeu o rodapé junto.
- **Três desvios do pedido:**
  - o campo continua escondido só da vista, e não com `display:none`, que o
    tiraria do teclado. O campo agora vem antes do rótulo, e o botão mostra o
    foco quando o campo o recebe;
  - o botão usa `#1a365d`, e não `#2563eb`;
  - a legenda usa `#5b6778`: o `#64748b` pedido dava 4,4 de contraste sobre o
    fundo do hover.

**Verificação:** feita por sonda em Node contra um DOM mínimo, com 11
conferências passando. Três sabotagens acusadas:
- escolher o arquivo sem disparar o envio;
- legenda sem o aviso do `.rar`;
- botão sem ligação com o campo.

A conferência do envio falhou duas vezes na primeira rodada, as duas por defeito
da sonda: um arquivo fictício que o `FormData` recusa, e o pedido do token
contra falsificação respondido com erro. Nas duas, o envio parava antes de
sair. **Não foi aberta em navegador.**

### Revisão de 30/09/2026 — a R04 lê a tributação integral declarada pelo catálogo

O texto acima e as revisões anteriores ficam como estão. Esta registra a primeira
mudança de regra desde a Etapa 3. Ela foi pedida pelo usuário, com autorização
prévia e lista fechada de arquivos.

**O critério antigo, e por que falhava.** A R04 1.0.0 considerava "tributação
integral" o código que o catálogo não marcava como benefício **e** que não
informava redução (`percentualReducao` vazio). A revisão de 14/09/2026 já dizia
que "a regra acima lê `0` como redução declarada", e é exatamente esse o defeito.
Um código integral transcrito da fonte com `0;0` nas colunas de redução fica com
redução **presente**, igual a zero. Para a regra, isso era "não integral", e a
resposta era `CONFORME` justamente no caso que ela existe para apontar. A sonda
mostrou o efeito numa nota com NCM em anexo e código de tributação integral: R04
`CONFORME`. Em 102 notas montadas sobre os códigos com redução zero do catálogo
de exemplos, a R04 1.0.0 não disparou em nenhuma.

Não havia como consertar isso dentro do critério antigo. Ler `0` como "sem
redução" apagaria a distinção da D002 entre não declarado e declarado zero. E
o fato de o código ser integral não se deduz da redução: há código com redução
zero que não é de tributação integral.

**O critério novo (R04 1.1.0).** O catálogo passa a declarar o fato, na coluna
`tributacaoIntegral` de `classificacao-tributaria.csv`:

- `S`, com NCM em anexo → `ACHADO`, **continua INFORMATIVA**;
- `N` → `CONFORME`, qualquer que seja a redução, inclusive zero;
- não declarado → `NAO_AVALIADO`, com motivo que cita o código e a coluna. Vale
  **também quando o código tem benefício marcado**: a regra não deduz "integral"
  nem "não integral" de nenhum outro campo.

A redução deixou de ser critério. Severidade, evidências e valor em risco não
mudaram.

**Decisão do usuário (seção 5), fonte informada: LC 214/2025.**

- `tributacaoIntegral = S` para os códigos com CST `000`.
- `tributacaoIntegral = N` para os códigos com CST `010`, `011`, `200`, `221`,
  `222`, `400`, `410`, `510`, `515`, `550`, `620`, `800`, `810`, `811`, `820` e
  `830`.

O sistema não sabe isso, e nada no código o afirma. A decisão vive **só** no CSV
de exemplos, que ganhou a coluna e mais nada: 5 linhas `S` e 156 `N`. O
cabeçalho de comentários, a linha `200025` e os demais valores ficaram como
estavam, conferido linha a linha contra cópia anterior.

**Invariante declarada pelo usuário**, aplicada pelo importador: `S` com
`indicadorDeBeneficio = true`, ou `S` com redução diferente de zero, é incoerente
e recusa a linha. `S` com redução em branco é aceito, também por decisão do
usuário. A invariante mora no importador, não em `ClassificacaoTributaria`.

**O importador.**

- Coluna ausente → tudo não declarado. Um arquivo anterior à coluna continua
  importando.
- Célula em branco → não declarado.
- Valor diferente de `S` ou `N` → recusa a linha, com linha, coluna e valor.
  Maiúsculas e minúsculas contam, como em `natureza`.
- O tudo ou nada da Etapa 12 continua: uma linha recusada recusa a carga inteira.

**Versões novas.** R04 `1.0.0` → `1.1.0`, e `ConjuntoRegras.VERSAO_PADRAO`
`2026.1` → `2026.2`. As outras seis regras continuam em `1.0.0`. Pela D006, a
identidade do apontamento leva a versão da regra, e um apontamento da R04 em
`1.0.0` não se confunde com um em `1.1.0`. Antes da mudança, o banco local tinha
**0** apontamentos da R04 (de 16), **0** tratativas da R04 (de 0), **0**
registros de tratativa da R04 e **15** avaliações não concluídas da R04, em 25
execuções. Nenhuma tratativa foi reaberta, porque não havia nenhuma.

**Consequência, registrada a pedido do usuário.** As cargas de catálogo já
gravadas no banco têm `tributacao_integral` nula: a V14 cria a coluna vazia, sem
`INSERT` nem `UPDATE`. Ao reprocessar contra elas, a R04 1.1.0 dá `NAO_AVALIADO`
onde o NCM está em anexo. Só uma carga nova, importada com a coluna, resolve. É
o mesmo custo da `natureza` na Etapa 11. A V14 não toca carga selada: `ALTER
TABLE` não dispara os gatilhos de linha da V11.

**Acurácia da Etapa 7.** Ela continua correspondendo ao código porque **nenhum
rótulo do gabarito da Etapa 7 é da R04**. Isso foi conferido, e não só
afirmado: a sonda rodou o motor inteiro com o conjunto 2026.1 (R04 1.0.0
reconstituída pelo critério antigo) e com o 2026.2, sobre as notas de
`src/test/resources/documentos`, comparando o desfecho célula a célula.

| Catálogo | Células | Mudam | Rotuladas vistas | Rotuladas que mudam |
|---|---|---|---|---|
| `exemplos/catalogo` | 42 | 0 | 14 de 15 | 0 |
| fictício da sonda | 42 | 0 | 14 de 15 | 0 |
| fictício de controle, sem a coluna | 42 | 3 (R04, `CONFORME` → `NAO_AVALIADO`) | 14 de 15 | 0 |

A célula rotulada que falta é a do documento corrompido, que o leitor não lê. As
3 mudanças do catálogo de controle são a consequência acima, em células sem
rótulo.

**Arquivos de etapas anteriores alterados, com autorização prévia:**

- `dominio/catalogo/ClassificacaoTributaria`: componente novo `Optional<Boolean>
  tributacaoIntegral`, que recusa nulo. Um construtor com a aridade antiga deixa
  o campo não declarado.
- `dominio/regras/RegraTratamentoDeAnexoNaoAproveitado` e `ConjuntoRegras`.
- `ImportadorClassificacaoTributariaCsv`, `ClassificacaoTributariaEntidade` e
  `MapeadorDeCatalogo`.
- Migration nova `V14__tributacao_integral.sql`.
- Nos testes, `RegraTratamentoDeAnexoNaoAproveitadoTest` e
  `QuatroCenariosDeConferenciaTest`: só o campo novo nos casos integrais e
  conformes, um teste novo de não declarado e o Javadoc do segundo. Nenhum teste
  renomeado, e `CenarioFicticio` não foi tocado.
- O literal de versão em `ConjuntoRegrasTest`, `AcuraciaPelaApiTest`,
  `HistoricoPelaApiTest` e `ComandoAvaliarAcuraciaTest`. Os três últimos foram
  trocados porque conferem uma execução feita pelo próprio teste.
- Novo: `TributacaoIntegralNaR04Test`.

Nenhum `import` de framework entrou em `dominio/`. Ficaram de fora, por
decisão do usuário: a R05, a severidade da R04, a exposição na API e na tela
(`ClassificacaoDoCatalogo`, `TratamentoExposto`) e `aliquota-vigente.csv`.

**Verificação.**

- `mvn test`: **1025 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé. Antes eram 1016. A diferença são os 8 de
  `TributacaoIntegralNaR04Test` e o teste de não declarado.
- Os testes novos foram escritos antes da implementação. Contra o código antigo,
  7 dos 8 falharam. O que passou é o de `N` com redução zero, que é a guarda
  contra disparar demais.
- Duas sabotagens, desfeitas por cópia:
  - voltar ao critério "redução vazia" derruba o teste de `S` com `0;0`
    (`expected: ACHADO but was: CONFORME`);
  - fazer a R04 disparar com `N` derruba o teste de `N` com redução zero
    (`expected: CONFORME but was: ACHADO`).
- Sonda contra o código novo, com `exemplos/catalogo`:
  - **a nota real de avaliação** dá R04 `CONFORME` → `ACHADO` INFORMATIVA, e as
    outras seis regras não mudam;
  - **102 notas** derivadas dela, uma por código com redução `0;0`: a R04 dispara
    em exatamente 5, que são os 5 códigos declarados `S`, e dá `CONFORME` nas 97
    declaradas `N`. Zero divergências.
- As notas derivadas foram apagadas depois da sonda. A sonda não é versionada e
  não imprime chave.
- A nota real estava em `src/main/resources/schemas/`, e por isso ia para
  `target/classes`. Foi movida para `dados/avaliacao/`, bloqueado pelo
  `.gitignore`.

### Revisão de 30/09 a 02/10/2026 — R03 e R05 em 1.1.0, e a cobertura por anexo

O texto acima e as revisões anteriores ficam como estão. Esta registra a segunda
mudança de regra desde a Etapa 3, pedida pelo usuário com autorização prévia e
lista fechada de arquivos, em três rodadas: R05, R03 e cobertura por anexo.

**Por que as duas regras entraram juntas.** A nota de avaliação `TESTE_02`
(NCM `31010000`, cClassTrib `200028`, CST `200`, `gRed` com `pRedAliq` 60) saía
com dois erros que se anulavam na tela:

- **R05 GRAVE, falso positivo.** A R05 1.0.0 conferia o valor contra a alíquota
  cheia, e a redução declarada não entrava na conta.
- **R03 CONFORME, falso negativo.** A R03 1.0.0 aceitava o NCM em **qualquer**
  anexo. `200028` é serviço de educação (Anexo II), e o NCM da nota está só no
  Anexo IX.

Corrigir só a R05 deixaria a nota inteira sem divergência. Por isso as duas
mudaram no mesmo salto de conjunto.

**Versões.** R03 `1.0.0` → `1.1.0`, R05 `1.0.0` → `1.1.0`, e
`ConjuntoRegras.VERSAO_PADRAO` `2026.2` → `2026.3`. A R04 continua em `1.1.0`,
e as outras quatro regras em `1.0.0`. Severidades não mudaram. Antes da
mudança, o banco local tinha:

- execuções: 25 com o conjunto `2026.1`, 3 com o `2026.2` e nenhuma com o
  `2026.3`;
- R05 1.0.0: 8 apontamentos e 18 avaliações não concluídas;
- R03 1.0.0: nenhum apontamento e 37 avaliações não concluídas;
- nenhuma tratativa nem registro de tratativa de nenhuma das duas.

Nenhuma tratativa foi reaberta, porque não havia nenhuma. O banco local está na
`V14` do Flyway; a `V15` e a `V16` entram na próxima subida.

#### Fontes e arquivos de decisão

**Fontes informadas pelo usuário (seção 5):**

- a Tabela de Classificação Tributária do IBS/CBS, do Portal da Conformidade
  Fácil (`https://dfe-portal.svrs.rs.gov.br/Cff/ClassificacaoTributaria`),
  página obtida em 30/09/2026;
- a LC 214/2025, texto consolidado com as alterações da LC 227/2026 (Planalto),
  conferido em **[data não informada pelo usuário]**.

**Arquivos de decisão**, em `dados/decisoes/`, que o `.gitignore` bloqueia:

- `d2-anexos-admitidos.csv`: colunas
  `codigo;cst;reducao;descricaoOficial;anexosAdmitidos;trechoQueJustifica`. São
  61 linhas, uma por código com `indicadorDeBeneficio = true`: 42 `NENHUM` e 19
  com anexo. Foi gerado pela regra de extração do usuário e conferido por ele
  linha a linha.
- `anexos-declarados.csv`: a lista de anexos válidos e quais estão carregados.
  Foi preenchido pelo usuário e copiado sem alteração para
  `exemplos/catalogo/anexos-declarados.csv`.

O sistema não sabe nada disso. As decisões vivem só nesses arquivos e nos CSV
de `exemplos/catalogo`.

#### As decisões D1 a D9 do usuário

- **D1.** `reducao_cbs` e `reducao_ibs` do catálogo são percentuais de redução da
  **alíquota** (`pRedCBS` e `pRedIBS` da tabela oficial). A redução de base
  (CST `222`; CST `210` com o indicador `ind_RedutorBC`) não é representada por
  essas colunas. Não foi criada coluna de incidência.
  *(Emendada pela D017, 03/10/2026: passou a existir a coluna
  `reducaoIncideSobre`; não declarado continua sendo alíquota.)*
- **D2.** Coluna nova `anexosAdmitidos` em `classificacao-tributaria.csv`,
  preenchida exatamente com a coluna de mesmo nome de `d2-anexos-admitidos.csv`.
  O formato é uma lista de identificadores do `item-anexo.csv` separados por
  `|`, ou `NENHUM`. Códigos ausentes do arquivo não foram preenchidos.
  - **Regra de extração, declarada pelo usuário:** os anexos citados na
    descrição oficial do código no formato "(Anexo N)". Descrição sem citação dá
    `NENHUM`.
  - **O único caso fora da regra foi o `200006`.** A descrição longa cita o
    Anexo XII para dispositivos *não* listados nele. Ficou marcado `REVISAR`, e
    o usuário decidiu `NENHUM`.
- **D3.** As linhas do ANEXO-IX em `item-anexo.csv` passam a citar a fonte
  "LC 214/2025, art. 138, Anexo IX".
- **D4.** Redução em branco no catálogo: R05 `NAO_AVALIADO`, com motivo (D002).
  Código fora do catálogo, ou item sem cClassTrib: fator 1, como na 1.0.0.
  *(Emendada pela D016, 03/10/2026: "código fora do catálogo: fator 1" vale só
  dentro da cobertura declarada da tabela de classificações; fora dela, a R05
  1.2.0 dá `NAO_AVALIADO`. Item sem cClassTrib continua com fator 1.)*
- **D5.** CST `222` ou `210` com redução diferente de zero no catálogo: R05
  `NAO_AVALIADO`, com o motivo de que redução de base não é suportada. Nos
  demais casos o fator incide sobre a alíquota.
  *(Emendada pela D017, 03/10/2026: a redução de base deixou de ser reconhecida
  pelo CST, escrito em código, e passou a ser declarada pela carga, com
  `reducaoIncideSobre = BASE`.)*
- **D6.** A lista de anexos válidos é declarada pelo usuário em
  `anexos-declarados.csv`, com as colunas
  `identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza`.
  - `tipoDeCodigo` aceita `NCM`, `NBS` ou `NCM_E_NBS`.
  - `natureza` mantém o sentido da Etapa 11 (`FICTICIO` ou `NORMATIVO`).
  - A tabela entra na procedência da carga como `ANEXO_DECLARADO`: é a quinta
    entrada de `NaturezaDaCarga` e conta no cálculo da situação. Carga sem ela
    continua com quatro entradas e a mesma situação de antes.
- **D7.** Cobertura **por anexo**: um anexo está carregado numa data somente se
  tem `vigenciaInicio` e a data cai na vigência.
  - Anexo declarado sem vigência existe, e serve para validar identificadores,
    mas não está carregado.
  - Anexo `NBS` declarado carregado e sem nenhuma linha no `item-anexo.csv` está
    **carregado e vazio de NCM**, e não "não carregado".
- **D8.** A R03 1.1.0:
  - `NENHUM`: não exige anexo;
  - `CONFORME` se o NCM está em algum anexo admitido e carregado na data;
  - `ACHADO` somente se **todos** os anexos admitidos estão carregados na data e
    o NCM não está em nenhum deles;
  - qualquer outro caso é `NAO_AVALIADO`, com o motivo "anexo admitido não
    carregado [lista]". Isso inclui a cobertura parcial: um código com dois
    anexos admitidos, só um carregado, e o NCM fora do carregado;
  - continuam os motivos que já existiam: cobertura da tabela fora da data, item
    sem NCM e código sem `anexosAdmitidos` declarado.
- **D9.** Validação da carga, completa e por substituição:
  - identificador de `anexosAdmitidos` fora da lista declarada recusa a carga
    inteira;
  - código que cita anexo, com `anexos-declarados.csv` ausente, recusa a carga;
  - cargas antigas gravadas no banco, sem cobertura por anexo, dão R03
    `NAO_AVALIADO` para os códigos com anexo, sem erro;
  - esta checagem **substituiu** a de 30/09/2026, que conferia o identificador
    contra as linhas do `item-anexo.csv`. Aquela recusava `exemplos/catalogo`
    inteiro, com 13 anexos citados e sem nenhuma linha.

**O sentido da vigência em `anexos-declarados.csv`.** Nesse arquivo,
`vigenciaInicio` preenchida quer dizer "este anexo está carregado **por
completo** no `item-anexo.csv` a partir desta data". **Não é a vigência da
lei.** A vigência do vínculo entre NCM e anexo continua nas linhas do
`item-anexo.csv`. É por isso que anexo com linhas pode estar declarado sem
vigência: as linhas existem, mas são recorte, e a R03 não pode concluir "o NCM
não está no anexo" a partir de um recorte.

No catálogo de exemplos, só o **ANEXO-II** e o **ANEXO-III** têm vigência. São
os dois `NBS`, sem linha nenhuma no `item-anexo.csv`, e portanto carregados e
vazios de NCM. Os demais 15 anexos estão declarados sem vigência, inclusive o
IV, o V, o VI e o IX, que têm linhas.

#### O `item-anexo.csv` de exemplos, corrigido e ampliado pelo usuário

O arquivo passou de 48 para 196 linhas, todas `REDUCAO_60`, `NORMATIVO` e com
vigência desde 2026-01-01:

| Anexo | Linhas | Fonte |
|---|---|---|
| ANEXO-IX | 48 | LC 214/2025, art. 138, Anexo IX |
| ANEXO-IV | 64 | LC 214/2025, art. 131, Anexo IV |
| ANEXO-V | 20 | LC 214/2025, art. 132, Anexo V |
| ANEXO-VI | 64 | LC 214/2025, art. 133, Anexo VI |

- **Fontes:** corrigidas pelo usuário, uma por anexo. Antes citavam o art. 129.
  A D3 tinha corrigido só o ANEXO-IX.
- **Os quatro são recortes, e não os anexos inteiros.** Mesmo assim, as linhas
  declaram `natureza = NORMATIVO`. Isso é correto: cada linha transcreve a
  norma, e o que falta são linhas, não exatidão. A incompletude é dita em outro
  lugar, no `anexos-declarados.csv`, onde os quatro anexos estão sem vigência.
- **Datas:** a `vigenciaInicio` das 64 linhas do ANEXO-IV veio em `01/01/2026` e
  foi trocada para `2026-01-01`, com autorização. Nenhuma outra coluna mudou.
- **NCM em dois anexos:** só dois, `38249989` (IX e IV) e `29152100` (IX e VI).
  A chave do item de anexo é o par NCM e anexo, no domínio, na carga e no
  banco, e aceita isso.
- **`cobertura.csv`:** a linha `ITEM_ANEXO` passou a citar a fonte "LC 214/2025,
  Anexos IV, V, VI e IX (consolidado LC 227/2026), recortes". O cabeçalho
  passou a dizer a natureza declarada tabela por tabela, e todas são
  `NORMATIVO`. As linhas `CLASSIFICACAO_TRIBUTARIA` e `NCM` continuam com
  "FONTE FICTICIA DE EXEMPLO v0.0", e a linha de comentário "Nao use este
  arquivo como referencia normativa." continua lá. Nenhuma das duas foi pedida.

#### A premissa da Etapa 11 que caiu

`QuatroCenariosDeConferenciaTest` afirmava que **o mesmo documento produz a
mesma situação nos quatro cenários**. O que mudava entre eles era só a carga. Isso
só valia porque a R05 1.0.0 ignorava a redução. A nota fictícia
`nfe-item-completo.xml` traz valores cheios, e o cenário "diferenciado
aproveitado" (`comReducao()`, redução 99,99) concluía sem divergência
**exatamente porque a redução não entrava na conta**.

Com a R05 1.1.0:

- `comReducao()` passou a declarar redução 60;
- o cenário diferenciado usa uma nota fictícia própria,
  `nfe-item-completo-reducao-60.xml`, com os valores calculados com 60% de
  redução sobre as alíquotas fictícias da carga. `nfe-item-completo.xml` não foi
  alterada;
- `oMesmoDocumentoDeveMostrarEnquadramentosDiferentesEmCargasDiferentes`
  continua com a mesma nota nas duas cargas, e passou a afirmar situações
  **diferentes**: `SEM_DIVERGENCIA_IDENTIFICADA` sem anexo, e
  `POSSIVEL_DIVERGENCIA` com anexo e redução, com a R05 como origem. A prova de
  que o enquadramento vem do catálogo passou a ser "os mesmos bytes produzem
  resultados diferentes sob catálogos diferentes";
- `integral()`, nesse teste, e `carga()`, em `HistoricoPelaApiTest`, passaram de
  redução não declarada para zero declarado. Pela D4, não declarado agora é
  `NAO_AVALIADO`.

Nenhum desfecho esperado mudou.

#### Arquivos

**Alterados, de etapas anteriores, com autorização prévia:**

- `dominio/`: `ClassificacaoTributaria` (componente `anexosAdmitidos`),
  `CoberturaDoCatalogo` (lista de anexos declarados, com o construtor antigo
  mantido), `RegraValorDeTributoConfere`, `RegraBeneficioExigeNcmEmAnexo` e
  `ConjuntoRegras`. Neste último mudaram a versão e a linha que entrega a
  cobertura à R03. Nenhum `import` de framework.
- `aplicacao/catalogo/`: `CargaDeCatalogo` (validação da D9 na montagem, o que
  cobre a edição), `NaturezaDaCarga` e `SubstituicaoDeTabelas`.
- `infraestrutura/`: `ImportadorClassificacaoTributariaCsv`,
  `LeitorDeCatalogoEmCsv` (inclusive a substituição),
  `ClassificacaoTributariaEntidade`, `MapeadorDeCatalogo`,
  `NaturezaDaCargaNoBanco`, `ProvedorDeCatalogoNoBanco`,
  `RepositorioDeCargaDeCatalogoNoBanco` e `AcervoDeCargasNoBanco`.
- Testes: `ConjuntoRegrasTest`, `RegraBeneficioExigeNcmEmAnexoTest`,
  `QuatroCenariosDeConferenciaTest`, `HistoricoPelaApiTest`,
  `AcuraciaPelaApiTest` e `ComandoAvaliarAcuraciaTest`. Em
  `RegraBeneficioExigeNcmEmAnexoTest`, todo teste que espera `NAO_AVALIADO`
  passou a conferir também o motivo. Dois deles continuavam verdes por
  coincidência quando a R03 passou a exigir `anexosAdmitidos`.

**Novos:**

- `dominio/catalogo/AnexoDeclarado` e `TipoDeCodigoDoAnexo`;
- `ImportadorAnexosDeclaradosCsv`, `AnexoDeclaradoEntidade` e
  `AnexoDeclaradoJpa`;
- `V15__anexos_admitidos.sql`, com uma coluna anulável, e
  `V16__anexo_declarado.sql`, com uma tabela vazia e o gatilho de carga selada.
  Nenhuma das duas tem `INSERT` ou `UPDATE`;
- sete classes de teste e uma nota fictícia (abaixo).

A API e o JavaScript não mudaram de código. O conteúdo exposto mudou: a faixa de
procedência pode trazer a quinta tabela.

#### Verificação

**Suíte:**

- antes: 1025 testes;
- depois da R05 e da R03: 1048;
- depois da cobertura por anexo: 1070;
- na entrega: **1070 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé, e o `git status` idêntico antes e depois.

**Testes novos, escritos antes da implementação e com dados fictícios:**

- `RegraValorDeTributoConfereComReducaoTest`,
  `RegraBeneficioExigeNcmEmAnexoAdmitidoTest`, `AnexosAdmitidosNoCsvTest` e
  `AnexoAdmitidoInexistenteNaCargaTest`: 20 testes, 14 vermelhos antes;
- `CoberturaPorAnexoNaR03Test`, `AnexosDeclaradosNaCargaTest` e
  `FaixaComAnexosDeclaradosTest`: 22 testes, 14 vermelhos antes.

**Sabotagens, todas desfeitas por cópia e conferidas idênticas ao original:**

| Sabotagem | Resultado |
|---|---|
| R05 ignora a redução | 3 de 18 falham |
| R05 lê redução em branco como zero | 1 de 18 falha |
| R05 aplica a redução com CST de redução de base | 1 de 18 falha |
| R03 volta à cobertura global | 4 de 20 falham |
| R03 volta à versão de 30/09 | 5 de 20 falham |
| R03 trata anexo `NBS` vazio como não carregado | 1 de 20 falha |
| R03, troca inofensiva de controle | 0 de 20 |
| D9 sem conferência na carga editada | 1 de 13 falha |

A volta à cobertura global **não** derruba o teste do anexo vazio, ao contrário
do que se esperava. Na cobertura global todo anexo conta como carregado, então o
anexo vazio continua dando `ACHADO`. O erro que esse teste pega é o inverso, e
foi a terceira sabotagem da R03 que o mostrou.

**Sonda com `exemplos/catalogo`**, em `/tmp`, pelo leitor real e pelo motor
inteiro, com notas derivadas das de avaliação por troca de `cClassTrib` e `NCM`.
A sonda não é versionada e não imprime chave.

- A carga é aceita: 161 classificações, 196 itens de anexo e 17 anexos
  declarados. As cinco tabelas são `NORMATIVO`, e a situação é `NORMATIVO`.
- Resultados por nota:

| Nota | R03 | R04 | R05 |
|---|---|---|---|
| `TESTE_02` (`200028`, NCM `31010000`) | `ACHADO` (ANEXO-II carregado e vazio) | `CONFORME` | `CONFORME` |
| `200038`, NCM `31010000` | `NAO_AVALIADO`, "anexo admitido não carregado [ANEXO-IX]" | `CONFORME` | `CONFORME` |
| `200030`, NCM `90189099` | `NAO_AVALIADO`, "[ANEXO-IV]" | `CONFORME` | `CONFORME` |
| `200034`, NCM `31010000` | `NAO_AVALIADO`, "[ANEXO-VII]" | `CONFORME` | `CONFORME` |
| `200028`, NCM `90189099` | `ACHADO` (ANEXO-II carregado e vazio) | `CONFORME` | `CONFORME` |
| `200001` (`NENHUM`), NCM `31010000` | `CONFORME`: não exige anexo | `CONFORME` | `ACHADO`* |
| `TESTE_01` (`000001`, NCM `31010000`) | `CONFORME` | `ACHADO` INFORMATIVA (ANEXO-IX) | `CONFORME` |
| `000001`, NCM `90189099` | `CONFORME` | `ACHADO` INFORMATIVA (ANEXO-IV) | `CONFORME` |
| `515001`, NCM `31010000` | `NAO_AVALIADO`, "[ANEXO-IX]" | `CONFORME` | `CONFORME` |

\* A nota foi derivada da `TESTE_02`, com valores reduzidos em 60%, e o
`200001` declara redução 100. O `ACHADO` é a conta da R05 sobre valores que não
foram feitos para esse código, e não diz nada sobre o código.

A `TESTE_01` não mudou de desfecho em relação à rodada anterior. Na R04, os dois
`000001` disparam pelas linhas do recorte, embora o ANEXO-IX e o ANEXO-IV
estejam declarados não carregados: a R04 não lê a cobertura por anexo. No
`515001`, a R05 dá `CONFORME` porque aplica a redução de 60% como em qualquer
código. **O diferimento não é tratado.** Nenhuma classe de `src/main` menciona
diferimento, e a nota traz `vDif` zerado.

**Acurácia da Etapa 7.** Os rótulos do gabarito que dependem das regras
alteradas são os **6 da R05**: 3 `ACHADO` e 3 `CONFORME`. Nenhum é da R03 nem da
R04. Os outros 9 são da R01 (2), da R02 (6) e da R06 (1).

**Não há commit no estado `2026.2`:** os oito commits com `ConjuntoRegras` estão
em `2026.1`. O estado `2026.2` foi reconstruído em `/tmp` a partir das cópias de
segurança da sessão, depois de conferir a lista contra `git status`, os não
rastreados e as datas de modificação:

- 16 arquivos de produção foram restaurados;
- 7 arquivos novos foram removidos;
- o resto de `src/main` ficou idêntico ao repositório;
- 12 arquivos de `dominio/regras` só tinham a data mudada (um `touch` de
  recompilação), e 11 deles são iguais ao último commit. O décimo segundo é a
  R04, que difere do commit só pela mudança de 30/09 e não foi escrita por
  esta mudança.

Comparação célula a célula, com o comparador real da Etapa 7:

| Estado | Arquivos | Documentos | Avaliações | R05 | Consolidado |
|---|---|---|---|---|---|
| `2026.2`: código, catálogo e pasta de então | 5 | 4 | 42 | VP1 FP2 FN0 VN0 NAv3 | VP1 FP3 FN0 VN0 NAv10 SemAval1 |
| Final: `2026.3` | 6 | 5 | 49 | VP1 FP2 FN0 VN0 NAv3 | VP1 FP3 FN0 VN0 NAv10 SemAval1 |

**Nenhuma das 15 células rotuladas muda.** Das 15, 14 são legíveis; a do
documento corrompido é `SemAval` nos dois estados. As métricas por regra são
idênticas.

Duas observações saíram da comparação:

- **A pasta `src/test/resources/documentos` ganhou um sexto XML.**
  `nfe-item-completo-reducao-60.xml`, criado nesta mudança, tem a mesma chave de
  acesso de `nfe-item-completo.xml`. Para o `avaliar-acuracia` com
  `--origem=src/test/resources/documentos`, os 3 rótulos dessa chave passam a
  ser avaliados duas vezes. O comparador aceita porque os desfechos coincidem
  nas sete regras, mas recusaria a medição se algum dia divergissem. Por isso
  mudam "documentos auditados" e "avaliações", e as métricas não.
- **O resultado esperado escrito no cabeçalho do `gabarito-exemplo.csv` já não
  corresponde** em nenhum dos dois estados. Por exemplo, a R01 dá `FP1` onde o
  cabeçalho espera `VN1`. Como é igual nos dois, não é efeito desta mudança. A
  causa não foi investigada.

#### Consequências declaradas

- **Cargas gravadas antes da V15 e da V16** têm `anexos_admitidos` nulo e
  nenhuma linha em `anexo_declarado`. Ao reprocessar contra elas:
  - a R03 dá `NAO_AVALIADO` para todo código de benefício;
  - a R05 dá `NAO_AVALIADO` onde a redução não foi declarada.

  Só uma carga nova resolve, como na `natureza` (Etapa 11) e na
  `tributacaoIntegral` (30/09).
- **Um código que admite o ANEXO-II ou o ANEXO-III dá R03 `ACHADO` para
  qualquer NCM.** Os dois estão declarados carregados e são de `NBS`. É o
  desenho da D7, e é o caso da `TESTE_02`.
- **Todo código que admite um anexo declarado não carregado dá R03
  `NAO_AVALIADO`** enquanto o anexo não for carregado por completo.

#### Pendências

- **A R04 depende de o anexo estar completo no `item-anexo.csv`.** Com recorte,
  um NCM fora das linhas carregadas não dispara a R04. Ela também não lê a
  cobertura por anexo da D7.
- **Carregar por completo os Anexos IV, V, VI e IX**, com NCM e NBS, e os
  demais. Só então declarar a vigência deles no `anexos-declarados.csv`.
- **Casar por NCM não prova o enquadramento** nos itens "Ex" com descrição
  restrita. O NCM pode estar no anexo e o produto não ser o descrito.
- **Diferimento na R05 (CST `515`).** A R05 aplica a redução e ignora o
  diferimento.
- **Composição do `vBC`**, com a exclusão do ICMS. A R05 confere o valor contra
  a base declarada, sem conferir como a base foi composta.
- **Cabeçalho do `aliquota-vigente.csv`.** Ele diz que os percentuais são
  impossíveis de confundir com alíquota real e que não são referência
  normativa, mas as três linhas declaram `natureza = NORMATIVO`.
- **O rótulo "versão" e os nomes técnicos na faixa de procedência.** A tela
  escreve os nomes das tabelas crus (`ANEXO_DECLARADO`, `ITEM_ANEXO` e os
  demais), sem rótulo legível.
- **A busca de carga por versão ordena como texto.** `carga_catalogo.versao` é
  texto livre e entra como desempate depois de `importado_em desc`, em seis
  lugares: `AcervoDeCargasNoBanco` (duas vezes), `CargaCatalogoJpa` (usado por
  `ProvedorDeCatalogoNoBanco` e `RepositorioDeCargaDeCatalogoNoBanco`),
  `ProvedorDaCargaEsperada` e `ProvedorDeCatalogoQueSela`. Só pesa com duas
  cargas importadas no mesmo instante.

---

## D013 — Identidade, perfis, e a carga de catálogo que não muda depois de usada

**Etapa:** 12 — Autenticação, perfis de usuário e CRUD de usuários e de cargas
**Status:** Aceita

> Emenda a **D009** nas exclusões de escopo "sem autenticação" e "sem
> multiusuário", e a **D009/D012** na parte que mantinha importar catálogo e
> tratar achado fora da API. Emenda a **D007** na parte da justificativa da
> tratativa na planilha. O que cada uma decidiu continua registrado onde está.
>
> **Não emenda:** D001 (domínio sem framework), D002 (ausência é um estado), D003
> (consulta normativa na data do documento), D004 (silêncio do catálogo só dentro
> da cobertura), D006 (identidade do apontamento e da tratativa pelo conteúdo) e
> D008 (acurácia por gabarito). **Nada sob `dominio/` foi alterado nesta etapa**,
> e a medição da Etapa 7 continua correspondendo ao código.
>
> "Multiusuário" não é "multiempresa". A seção 4 do CLAUDE.md continua
> intacta: há uma empresa só, e várias pessoas dela.

### Contexto

Até a Etapa 11 o sistema não sabia quem o usava. Isso tinha três consequências,
todas registradas nas decisões anteriores como limites conscientes:

- importar catálogo e tratar achado ficaram fora da API, porque o primeiro decide
  o que o sistema afirma sobre a norma e o segundo é juízo de uma pessoa, e não
  havia pessoa identificada (D009, D012);
- a tratativa gravava decisão, justificativa e data, mas não quem decidiu;
- o bind em `127.0.0.1` era o único controle de acesso.

A etapa pede identidade, três perfis, CRUD de usuários e de cargas, e a tratativa
pela web. A armadilha estava nas cargas: o `ProvedorDeCatalogoPorVersao` reabre
cada análise com a carga que ela usou. Editar uma linha de carga usada mudaria o
fundamento que uma análise passada cita, sem que nada avisasse. É a mesma família
de falso negativo silencioso que o R04 corrigiu no motor.

Três premissas do pedido não conferiam com o código, e foram corrigidas antes de
qualquer linha:

- **a justificativa não tinha opt-in na planilha.** O opt-in existia só na API;
  `ExportadorXlsx` a escrevia sempre, e o guarda de vazamento da Etapa 6 nunca a
  via, porque o papel que ele montava não tinha tratativa;
- **a importação parava na primeira linha recusada**, e não listava todas;
- **a "guarda de cobertura declarada sobre tabela sem registros" não existia.**
  Existia a recusa de carga sem registro em tabela nenhuma, e a de arquivo
  ausente. Uma tabela só com cabeçalho e cobertura declarada era aceita.

### Decisão

#### Três perfis, e a permissão conferida no servidor

| Perfil | Pode |
|---|---|
| `ADMINISTRADOR` | tudo o que o fiscal faz, mais usuários e cargas de catálogo |
| `FISCAL` | enviar nota, corrigir análise, registrar tratativa, ler tudo |
| `CONSULTA` | só ler |

A matriz, endpoint por endpoint, está em
`infraestrutura/seguranca/MatrizDePermissoes` e é aplicada no filtro do Spring
Security, **antes de qualquer controlador**. O que não está nela é negado a
todos (`/api/**` → `denyAll`). Execução, análise e achado **não têm PUT nem
DELETE para perfil nenhum**: são registro de auditoria, e a tratativa é o único
caminho de intervenção humana.

Esconder botão não é controle de acesso. As telas escondem o que o perfil não
pode fazer só por conveniência; o teste `PermissoesPorEndpointTest` chama cada
endpoint direto, por HTTP, com cada perfil, e confere 403 onde não pode e 401
sem sessão. A matriz esperada está **escrita à mão no teste**, e não lida da
matriz de produção — lida de lá, uma permissão alargada por engano seria
alargada junto na expectativa. O teste confere também que todo endpoint
registrado no Spring tem linha na matriz e que toda linha tem endpoint: endpoint
novo sem permissão decidida quebra o build.

#### Sessão no servidor, senha em BCrypt, e o usuário relido a cada pedido

- **Sessão no servidor**, com cookie `http-only` e `same-site=strict`. Sem
  "lembrar-me" e sem recuperação por e-mail. Token no navegador foi recusado: ele
  teria de morar onde o JavaScript da página alcança.
- **Token contra falsificação de pedido** em toda escrita, pego em
  `GET /api/sessao/csrf`. Inclusive no login.
- **BCrypt, custo 12**, com sal sorteado por senha e gravado dentro do próprio
  hash. Argon2 foi considerado; exigiria BouncyCastle, uma dependência a mais,
  sem ganho que se pague aqui.
- **A senha só existe no corpo do pedido.** Os tipos que a carregam —
  `SenhaInformada`, `HashDeSenha` e os três pedidos da API — têm `toString` que a
  omite. Isso não é cosmético: o Spring MVC, em nível de detalhe, loga o corpo
  lido pelo `toString`, e o teste de log mostra `senha=omitida` exatamente nessa
  linha.
- **A recusa de login é uma só** para login inexistente, senha errada e usuário
  desativado, e o BCrypt roda mesmo sem usuário, contra um hash de comparação,
  para o tempo de resposta não dizer quais logins existem.
- **O usuário é relido do banco a cada pedido** (`RecargaDoUsuario`). Rebaixar
  alguém vale no pedido seguinte, e desativar encerra a sessão na hora, sem
  esperar ela vencer.

**O bind continua em `127.0.0.1`.** Sem HTTPS, a senha trafegaria em texto
claro pela rede, e senha exposta é pior que nenhuma: dá a impressão de acesso
protegido. Abrir o bind exige HTTPS antes, e HTTPS continua fora do escopo.

#### Usuários: o último administrador, e desativar no lugar de apagar

- **O sistema nunca fica sem administrador ativo.** Excluir, rebaixar e
  desativar conferem isso numa transação com a tabela `usuario` travada, para
  dois administradores não se rebaixarem ao mesmo tempo.
- **Excluir quem já registrou tratativa ou correção de análise é desativar.** A
  tratativa é juízo humano registrado, e precisa continuar atribuída a alguém
  identificável. A pergunta "já registrou" olha o **histórico**, e não a decisão
  que vale hoje — quem foi sobrescrito por outra pessoa também registrou.
  `ON DELETE RESTRICT` é a segunda barreira: o banco recusa apagar mesmo que o
  código erre.
- **Nenhuma migration cria usuário.** O primeiro administrador nasce pelo comando
  `criar-administrador`, na máquina do sistema, com a senha digitada no console,
  sem eco, duas vezes. O mesmo comando é a recuperação de acesso: se o login já
  existe, redefine a senha, põe o perfil de administrador e reativa.

#### A tratativa grava quem decidiu, e o histórico só cresce

`Tratativa` (domínio) não tem autor, e **continua sem ter**. A autoria mora ao
lado, na aplicação e na infraestrutura:

- `ServicoDeTratativaAtribuida` grava a decisão que vale hoje na tabela
  `tratativa`, pelo mesmo `RepositorioTratativa` de sempre, e acrescenta uma linha
  em `tratativa_registro`, com o autor, na mesma transação;
- `tratativa_registro` **só recebe acréscimo**: um gatilho recusa `UPDATE` e
  `DELETE`. Tratar de novo muda a decisão que vale, e não apaga quem decidiu
  antes;
- as tratativas anteriores a esta etapa foram copiadas para o histórico **com
  autor nulo e o motivo escrito** — "registrada antes de o sistema identificar
  usuários". Ausência escrita, nunca suposta (D002);
- **quem decidiu vem da sessão**, nunca do corpo do pedido. Há teste mandando um
  `autorId` de outra pessoa no corpo e conferindo que ele é ignorado;
- pela CLI, `tratar-achado` passou a exigir `--usuario` e a pedir a senha no
  terminal. Consequência aceita: o comando não serve para roteiro automático.
  Tratativa é ato humano.

O `ServicoDeTratativa` da Etapa 5, que grava sem autor, **continua existindo**,
mas nenhuma entrada do sistema o usa mais para gravar.

#### A justificativa passou a ser opt-in também na planilha

`ExportadorXlsx` só escreve a justificativa se a instalação ligar
`auditoria.exportacao.expor-justificativa`. Desligada — o padrão —, a célula traz
o motivo da omissão. O guarda de vazamento da planilha passou a plantar um CNPJ e
uma razão social fictícios na justificativa e a conferir as duas direções: por
padrão nenhum dos dois sai no arquivo; ligada, os dois saem — o que prova que o
guarda olha a coluna certa.

#### Carga de catálogo: selada na entrega, imutável depois

**O selo.** `carga_catalogo.selada_em` é preenchida **no momento em que a carga
é entregue ao motor**, e não quando a análise termina de gravar. O
`ProvedorDeCatalogoQueSela` trava a linha da carga mais recente
(`select ... for update`), sela, e só então entrega o conteúdo. A edição de
rascunho trava a mesma linha e confere de novo que ela não está selada. Selar na
gravação deixaria uma janela: a análise lê o rascunho X, alguém o edita enquanto
o motor roda, e a execução grava "usei X" tendo avaliado o X antigo.

**O selo só anda num sentido.** Não volta a nulo nem se as análises que usaram a
carga forem apagadas por `recomecar-do-zero`: relatórios exportados antes
continuam citando a versão, e ela precisa continuar querendo dizer o mesmo
conteúdo. O selo vale também para a medição de acurácia, que entrega a carga ao
motor do mesmo jeito.

**Carga selada:**

- **exclusão recusada**, com a quantidade de análises que dependem dela. Com N = 0
  — análise que falhou depois do selo, ou acervo recomeçado —, a mensagem diz que
  a carga foi entregue a uma análise e que relatórios exportados podem citá-la;
- **edição cria carga nova**, com versão própria e `derivada_de` apontando para a
  original. A original fica intacta, byte a byte — o teste confere por resumo de
  todas as linhas de todas as tabelas. A carga nova passa a ser a mais recente, e
  é ela que as próximas análises usam.

**Rascunho** — carga nunca entregue a análise — é editado no lugar e excluído
livremente.

**Gatilhos no banco** recusam inserir, alterar ou apagar linha de qualquer tabela
de carga selada, apagar a carga selada, e tirar ou trocar o selo. É a mesma
função das restrições `check` do resto do esquema: barreira de última instância.

**A tela diz o efeito antes de confirmar.** `GET /api/cargas/{versao}` traz a
prévia — "esta carga é usada por N análise(s); salvar criará a versão X" — e o
botão de salvar diz o mesmo. O pedido de edição leva o efeito que a tela mostrou;
se ele mudou enquanto a pessoa editava, o servidor responde 409, **não grava
nada**, e devolve a prévia nova. Nunca troca "alterar rascunho" por "criar
versão" em silêncio.

**Edição é substituição de CSV**, uma ou mais tabelas; a que não vier é copiada
da origem, com a natureza que tinha. Formulário de linha foi recusado: seria uma
segunda porta de entrada para conteúdo normativo, e a seção 5 existe para não
haver segunda porta.

#### A importação recusa a carga inteira, com todas as linhas

Os quatro importadores e o `cobertura.csv` passaram a ler até o fim, juntando
cada recusa com arquivo, linha, coluna e valor, e a carga é recusada **numa
mensagem só**, sem gravar nada. Falta de coluna no cabeçalho aparece uma vez por
arquivo, e não uma vez por linha. A `natureza` continua obrigatória em toda linha
dos quatro arquivos de dados; ausente, a carga falha, e nunca assume valor.

Limite declarado: linha com número de campos diferente do cabeçalho interrompe a
leitura **daquele arquivo** — é o `LeitorCsv` que recusa, e ele não foi alterado —,
mas os outros arquivos continuam sendo lidos e os problemas deles aparecem.

A guarda nova, **cobertura declarada sobre tabela sem registro**, recusa a
carga. É o mesmo critério de "natureza declarada só em tabela com registro",
aplicado à cobertura. **Consequência que precisa ser dita:** como `cobertura.csv`
exige cobertura para as três tabelas (a D004 e o domínio pedem isso), as três —
classificação, NCM e item de anexo — passam a ser obrigatoriamente não vazias.
Só a de alíquotas, que não tem cobertura, pode vir só com o cabeçalho.

A guarda vale na importação por CSV (linha de comando e web) e na edição. Carga
montada direto em Java, como nos testes das Etapas 8 e 11, não passa por ela.

#### Corrigir a entrada é outra análise

Dado já auditado não é editável. `POST /api/analises/{id}/correcoes` recebe o
arquivo corrigido, roda uma análise **nova** — com o próprio `hash_entrada`,
correspondendo ao que ela processou — e grava em `correcao_de_analise` que ela
corrige a anterior. A anterior não muda. Tabela à parte, e não coluna em
`execucao_auditoria`, para não haver caminho de escrita na linha da execução.

### Consequência

- **Nada sob `dominio/` mudou.** A acurácia da Etapa 7 continua valendo.
- **Dependências novas:** `spring-boot-starter-security` e
  `spring-security-test` (só teste).
- **Migrations novas, todas sem dado normativo:** `V10` (usuário e histórico de
  tratativa, com a cópia das tratativas existentes sem autor), `V11` (selo,
  origem e gatilhos, com o selo retroativo nas cargas já citadas por execução),
  `V12` (vínculo de correção).
- **Toda a API exige sessão.** Os três testes HTTP das Etapas 8 e 11 passaram a
  entrar como administrador por `SessaoDeTeste`, pelo mesmo caminho do navegador.
  Um perfil de teste com a segurança desligada foi recusado: tornaria falso dizer
  que a segurança está testada.
- **A planilha mudou:** a coluna de justificativa traz o motivo da omissão, a não
  ser que a instalação ligue a exposição.
- **As três tabelas com cobertura não podem mais vir vazias.**
- **`tratar-achado` pede senha no terminal**, e sem terminal recusa.
- **O guarda `SpringWebNaoVazaDaApiTest` acusou a primeira versão** do pacote de
  segurança, que usava Jackson e um filtro do Spring MVC fora de
  `infraestrutura/api`. O guarda estava certo: o filtro virou
  `jakarta.servlet.Filter` puro e a resposta de recusa, JSON escrito à mão.

**Arquivos de etapas anteriores alterados, com autorização prévia e cláusula de
emenda:**

- Etapa 2 e 7: `LinhaCsv` (recusa com coluna e valor para quem implementa a
  interface nova `RecusaPorCampo`; as mensagens são as mesmas, e o gabarito da
  Etapa 7 recebe exatamente o que recebia);
- Etapa 2: `LeitorDeCatalogoEmCsv` e os quatro importadores;
- Etapa 5: `ComandoTratarAchado`;
- Etapa 6: `ExportadorXlsx`; nos testes, `NenhumIdentificadorEmTextoClaroNaExportacaoTest`
  (acréscimo), `ExportadorXlsxTest` (**uma linha**: o exportador do teste liga a
  justificativa) e `PapelDeTrabalhoDePontaAPontaTest` (**uma propriedade**, idem);
- Etapa 8: `application-api.properties`; nos testes, `ApiDeLeituraTest`
  (entra com sessão);
- Etapa 9: `js/telas/achado.js` (histórico e formulário de tratativa) e
  `tecnica.html`;
- Etapa 11: `index.html`, `js/conferencia/app.js`, `api.js`, `roteador.js` e
  `telas/resultado.js`; nos testes, `AnaliseDePontaAPontaTest` e
  `QuatroCenariosDeConferenciaTest` (entram com sessão);
- `pom.xml`.

`ConfiguracaoDaAuditoria`, `ProvedorDeCatalogoNoBanco`, `RepositorioTratativaNoBanco`,
`ServicoDeTratativa`, `ControladorDeAnalises`, `TratadorDeErrosDaApi`,
`ConfiguracaoDaApi`, `AchadoExposto` e `MontadorDeRespostas` **não foram
alterados**, embora estivessem na lista autorizada: o selo entrou como
decorador `@Primary`, a autoria em serviço e adaptador novos, as recusas novas
em `TratadorDeErrosDaIdentidade`, e o histórico da tratativa num endpoint
próprio.

**Verificação.** `mvn test`: **981 testes, nenhuma falha, nenhum pulado**, com o
Docker de pé. São os 904 da revisão de 14/09/2026 mais 77 novos, entre eles
`PermissoesPorEndpointTest`, `UsuariosPelaApiTest`, `CargasPelaApiTest`,
`TratativaECorrecaoPelaApiTest`, `ServicoDeUsuariosTest`, `ServicoDeCargasTest`,
`RecusaDaCargaInteiraTest` e `ComandosDeIdentidadeTest`.

Na direção contrária, treze sabotagens no código de produção, cada uma aplicada,
rodada contra os testes que a deveriam pegar e desfeita. Todas foram acusadas:
matriz com fiscal importando carga; filtro conferindo só sessão, e não perfil;
entrega ao motor sem selar; `toString` do pedido mostrando a senha; exclusão sem
a regra do último administrador; exclusão sempre apagando; justificativa ligada
por padrão na planilha; importação parando na primeira linha; guarda de
cobertura desligada; carga selada editada no lugar; gatilho da classificação
removido; filtro de recarga do usuário removido; conferência de perfil da
tratativa removida.

Duas sabotagens **passaram na primeira rodada**, e as duas eram furo de teste:

- editar carga selada no lugar mirou `EstadoDaCarga.efeitoDaEdicao()`, que não
  tinha chamador — o serviço decidia pelo selo direto. O serviço passou a usar o
  método, e a decisão mora num lugar só;
- sem o filtro de recarga, o teste de rebaixamento continuava verde, porque o
  serviço de tratativa também confere o perfil. O teste passou a usar a listagem
  de usuários, que só o filtro recusa.

O teste de senha em log **acusou vazamento na primeira rodada**, e o vazamento
era do cliente HTTP do próprio teste, que loga o corpo que envia. O log do
servidor, lido no mesmo arquivo, mostrava `senha=omitida`. O teste passou a
silenciar só o log do cliente, e a exigir que o servidor tenha logado o corpo
lido — sem isso, "a senha não aparece" passaria por não haver log.

**Interface:** sonda em Node contra um DOM mínimo, 22 conferências, e cinco
sabotagens numa cópia das páginas, todas acusadas. A primeira rodada das
sabotagens de interface "passou" inteira porque o executor chamou um `bash` sem
`node`, e nada rodou; o executor passou a exigir as 22 conferências rodadas em
cada sabotagem. A sonda não é versionada.

**Sistema empacotado:** o jar, contra um PostgreSQL descartável —
`criar-administrador` sem terminal recusa com código 2 e não grava; a restrição
do banco recusa senha em texto claro na coluna do hash; a página estática abre
sem sessão; a API sem sessão responde 401; login sem token, 403; senha errada,
401 com a recusa única; consulta lê execuções e recebe 403 ao listar usuários,
excluir carga e enviar nota; a senha não aparece no log do servidor.

~~**Pendência, não autorizada e por isso não feita:** três textos de etapas
anteriores ficaram falsos e estão fora da lista autorizada — a linha que
`ComandoServir` imprime ("Importar catálogo e tratar achado continuam na CLI"),
com a asserção correspondente em `ComandoServirTest`, e os comentários de classe
de `ControladorDeAnalises` e `ControladorDeExecucoes`.~~ **Corrigida em
24/09/2026, com autorização.** `ComandoServir` passou a imprimir que toda chamada
exige login e quem pode escrever, com cláusula registrando a frase anterior; o
teste foi renomeado para `deveAnunciarQueExigeLoginEQuemPodeEscrever` e passou a
exigir que a frase antiga não volte — com ela restaurada, ele falha. Os dois
controladores ganharam cláusula de emenda no comentário, sem mudança de código.

### Revisão de 03/10/2026 — a guarda de cobertura passa a conferir por anexo

A revisão adversarial de 03/10/2026 mostrou que a guarda decidida acima conferia
só a **tabela**: `item-anexo.csv` não vazio passava, mesmo que um anexo declarado
carregado em `anexos-declarados.csv` não tivesse nenhuma linha — bastava outro
anexo ter. Demonstrado com `exemplos/catalogo` sem as linhas do ANEXO-IX, mantido
declarado carregado: a carga era aceita, a R03 passava de 1 para 5 `ACHADO`
GRAVE no corpus de avaliação, inclusive na nota de controle, e a R04 caía, em
silêncio, de 2 apontamentos para 0. É o defeito que esta guarda existe para
impedir, uma granularidade abaixo.

**A guarda passou a exigir, para cada anexo declarado carregado de tipo `NCM` ou
`NCM_E_NBS`, ao menos uma linha em `item-anexo.csv` com aquele identificador e
vigência que se sobreponha ao período de carregamento.** A recusa é uma só,
nomeia cada anexo, o tipo e o período declarado, e diz o que falta. Vale nos
mesmos caminhos da guarda de tabela: importação pela linha de comando e pela
web, e edição de carga.

Duas decisões do usuário, postas antes de qualquer linha, porque o pedido
literal — "todo anexo declarado" — recusaria o próprio `exemplos/catalogo`, com
13 anexos sem linha, e contradiria a D012:

- **ficam de fora o anexo de `NBS` e o anexo declarado sem vigência.** O de NBS
  carregado e sem linha está "carregado e vazio de NCM" (D7), porque
  `item-anexo.csv` só guarda NCM; o sem vigência existe para validar
  identificador e não está carregado (D6, D9), e a R03 não conclui sobre ele;
- **a linha precisa valer no período de carregamento**, e não só existir: uma
  linha que vale apenas antes da data em que o anexo foi declarado carregado
  deixa o anexo vazio na data das notas do mesmo jeito.

`dominio/` não foi tocado. A sobreposição de períodos é conferida na própria
guarda, que fica em `aplicacao/catalogo`.

**Verificação.**

- `AnexoCarregadoSemLinhaTest`, pelo caminho real da importação, com CSV
  fictícios em diretório: 7 testes, escritos antes da mudança, **4 vermelhos**
  contra a guarda antiga — os quatro de recusa. Os três que passaram são os de
  aceitação: anexo com linha no período, anexo de NBS vazio, anexo de NCM sem
  vigência.
- Um teste da revisão de 01/10/2026 afirmava o caso agora recusado:
  `AnexoAdmitidoInexistenteNaCargaTest.deveAceitarAnexoDeclaradoMesmoSemNenhumaLinhaEmItemAnexo`
  declarava o anexo sem linha como de NCM. O teste existe para mostrar que a D9
  confere o identificador contra a lista declarada, e não contra
  `item-anexo.csv`; o próprio Javadoc dele cita o anexo de NBS como exemplo. O
  anexo do teste passou a ser de NBS, com comentário, e a asserção não mudou.
- Suíte: **1117 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé.
- Sabotagens, contra os mesmos 30 testes, cada arquivo restaurado e conferido
  por SHA-256: controle 0 de 30; guarda sem conferência por anexo, 4; qualquer
  linha valendo em qualquer vigência, 1; anexo `NCM_E_NBS` de fora, 1; anexo de
  NBS também cobrado, 3; anexo sem vigência também cobrado, 1; recusa nomeando só
  o primeiro anexo, 1. Todas acusadas.
- Pelo jar, com o cenário demonstrado (`exemplos/catalogo` sem as linhas do
  ANEXO-IX): o jar anterior aceitava a carga, com 148 itens de anexo; o novo
  recusa, com código 2, nomeando o ANEXO-IX, o tipo e o período. E
  `exemplos/catalogo` íntegro continua importando, com 196 itens de anexo.

---

## D014 — Apresentação: abas, colapso decidido no servidor, histórico no servidor e acurácia pelos arquivos da pessoa

**Etapa:** 13 — Apresentação
**Status:** Aceita

> Emenda a **D010** e a **D012** na organização das telas, e a **D008** na parte
> de onde a medição pode ser disparada. **Não emenda** a D008 no consolidado: ele
> continua micro, e a média macro foi pedida e **removida** (ver abaixo).
>
> **Nada sob `dominio/` mudou**, nenhuma regra, nenhum catálogo. A acurácia da
> Etapa 7 continua correspondendo ao código.

### Contexto

O resultado de uma análise ocupava uma página longa, com tudo aberto; o histórico
pedia as últimas vinte e cinco execuções e fazia uma segunda chamada por linha;
a acurácia só lia o CSV que o comando `avaliar-acuracia` já tinha escrito. A
etapa pede abas, colapso do que está conforme, histórico paginado e filtrado no
servidor, acurácia calculada a partir dos arquivos da pessoa, e acessibilidade.

Dois riscos atravessam a etapa, e são os de sempre do projeto: esconder
pendência — um lote com seis conformidades e quatro não concluídos abrir
parecendo limpo — e mostrar número sem dizer contra o que foi calculado.

### Decisão

#### O resumo fica fora das abas

Os quatro estados dos produtos, inclusive os zeros, a quantidade de produtos com
alguma verificação sem conclusão, os arquivos ilegíveis contados à parte, e a
versão do catálogo e das regras ficam num **resumo fixo acima das abas**, e não
dentro de nenhuma. "Não foi possível concluir" nunca fica atrás de uma aba que a
pessoa possa não abrir.

As abas — Resumo, Produtos, Não concluídos, Agrupamentos (só no lote) e Detalhes
técnicos — são detalhamento. A aba de não concluídos traz a contagem no rótulo, e
diz "(0)" em vez de sumir. As abas seguem o padrão ARIA, com setas, Home e End, e
a escolhida vai no endereço.

#### A regra de colapso, e quem a decide

> Nasce recolhida somente a explicação de uma verificação cujo estado é
> `SEM_DIVERGENCIA_IDENTIFICADA`; toda outra nasce aberta, e nenhum agrupamento
> nasce recolhido se contiver ao menos uma verificação que não seja
> `SEM_DIVERGENCIA_IDENTIFICADA`.

**Quem decide é o servidor**, em `RegraDeColapso` (aplicação), por lista de
permissão com um valor só: estado novo nasce aberto. A decisão sai nas respostas
como `recolhidaPorPadrao` (passo e verificação) e `recolhidoPorPadrao` (grupo), e
os construtores dessas respostas **recusam** recolher o que não é "sem
divergência" — segunda barreira.

A tela só obedece. `js/colapso.js` é o único módulo que cria `<details>`, não
menciona estado nenhum, e nasce aberto a não ser que receba `true`. Um teste de
código-fonte confere as três coisas, e confere que nenhum módulo compara estado
ou situação com código literal — é o que faz a regra sobreviver à próxima tela.
Recolher esconde a explicação; o selo do estado continua visível no resumo do
bloco.

A cláusula dos agrupamentos existe porque um grupo recolhido com uma pendência
dentro esconderia a pendência pela porta dos fundos, mesmo com a regra por
verificação correta.

**Consequência na visão técnica (Etapa 9):** os grupos de achados e de não
avaliados eram `<details>` fechados — pendência atrás de clique. Passaram pelo
mesmo auxiliar, e nascem abertos.

#### Histórico: paginado e filtrado no servidor

`GET /api/analises` devolve só a página pedida, da mais recente para a mais
antiga, com filtros por período, por situação mais grave presente, por quantidade
de produtos com possível divergência e por quem executou. Cada linha traz a
versão do catálogo e a das regras, e os quatro estados separados, com os zeros.
*(Emendado pela D020, 04/10/2026: a execução que não registrou os itens lidos —
a do comando `auditar` — vinha com quatro zeros, que não eram a contagem. Hoje
vem "não registrado", e nenhum filtro de situação ou de quantidade a exclui.)*

- **"Situação mais grave presente", e não "predominante".** Pela maioria, um
  lote com seis sem divergência e quatro não concluídos seria filtrado como sem
  divergência. O critério é a precedência dos estados, a mesma da situação do
  produto. **O rótulo do filtro na tela diz isso**, com a explicação ao lado.
- **O resumo por execução é gravado** em `resumo_da_execucao` (V13), calculado
  pelo mesmo `MontadorDaConferencia` que monta a tela — a tradução de desfecho em
  estado continua num lugar só. Execução sem resumo é completada na primeira
  consulta ao histórico. Os quatro números ficam em colunas separadas; não há
  coluna de total nem de soma.
- **Quem executou** fica em `autoria_da_execucao` (V13), gravado no envio pela
  web e na correção. **Execução da linha de comando fica com "executor não
  registrado", por desenho, e não por lacuna a preencher depois.** A CLI não tem
  pessoa logada, e execução anterior à Etapa 13 não gravava quem a disparou. Não
  se cria executor padrão nem se atribui a CLI a um usuário: isso seria afirmar
  quem executou sem saber. Quem executou análise é desativado, e não apagado,
  pela mesma regra da tratativa.

#### Acurácia pelos arquivos da pessoa

`POST /api/acuracia` recebe **as notas e o gabarito** — medir roda o motor de
novo, e o motor precisa das notas — e mede com o mesmo
`ServicoDeAvaliacaoDeAcuracia` e o mesmo `ComparadorDeGabarito` do comando. Nada
é recalculado na camada web, e nada é gravado. O gabarito é o mesmo CSV que o
comando consome. (A tela antiga não pedia xlsx: lia o CSV de resultado. Ela
continua na visão técnica.)

**Medir sela a carga.** Entregar a carga ao motor a sela, como já acontecia no
comando. Sem aviso, alguém selaria um rascunho sem querer ao medir, e descobriria
depois que não consegue mais editá-lo no lugar. Por isso a tela mostra, **antes
do botão**, "esta medição usará e selará a carga X", e o pedido leva a carga que
a tela mostrou. Se a mais recente mudou nesse meio tempo, o servidor recusa com
409, sem medir e sem selar — o mesmo padrão de "edição sem surpresa" da D013.
Medir é de fiscal e administrador; consulta vê a prévia e não mede.

**Toda linha de métrica carrega a versão do catálogo, a do conjunto de regras e a
cobertura**, e o construtor da resposta exige as três. Métrica sem item avaliado
sai "(indefinida)", escrita pelo servidor, nunca zero nem célula vazia. Não
avaliado fica fora de precisão, recall e F1, e aparece só na cobertura.

**O consolidado é micro**, e a tela diz isso. **A média macro por regra foi
pedida e removida.** A D008 a recusou porque obrigaria a decidir o que fazer com
as regras de métrica indefinida, e essa pergunta continua sem resposta
defensável. A preocupação que motivou o pedido — uma regra com muitas linhas
dominar o consolidado — já está coberta pela tabela por regra, sempre exibida ao
lado. Registrado aqui para o pedido não voltar sem uma resposta nova para aquela
pergunta.

#### Usabilidade

- **Contraste WCAG AA**, medido pela fórmula de luminância: o cinza das notas
  (3,41:1) e o amarelo e o azul usados como texto (2,05 e 4,19) reprovavam, e
  passaram a tons que dão 5,15, 5,62 e 5,58. As cores originais continuam nas
  bordas e nos fundos.
- Foco de teclado visível em tudo o que recebe foco; abas navegáveis por teclado;
  todo campo com rótulo associado.
- Cor nunca é a única codificação: estado com marca de forma e rótulo por
  extenso. Na impressão, todas as abas aparecem abertas. A planilha só usa cor
  no cabeçalho; o estado de cada linha sai em texto.
- Estados vazios dizem o que aconteceu e o próximo passo; operação demorada mostra
  o tempo decorrido; tabelas largas rolam no próprio contêiner.

### Consequência

- **Nada sob `dominio/` mudou.** Nenhuma dependência nova. Migration nova: `V13`,
  duas tabelas vazias.
- **Três endpoints de leitura e um de escrita novos**, todos na matriz de
  permissões: `GET /api/analises`, `GET /api/analises/{id}/autoria`,
  `GET /api/acuracia/previa` e `POST /api/acuracia`.
- **A primeira consulta ao histórico depois da atualização calcula o resumo de
  todas as execuções antigas**, uma vez.
- **Pendência:** a recusa de rótulo desconhecido no gabarito cita o valor e os
  aceitos, mas não o número da linha. Quem a lança é o domínio, e o
  `LeitorDeGabaritoCsv` (Etapa 7) não acrescenta a linha. Corrigir exige mexer
  naquele arquivo.

**Arquivos de etapas anteriores alterados**, como consequência do desenho
aprovado, cada um com cláusula de emenda:

- Etapa 8: `PassoExposto`, `ProdutoExposto`, `GrupoExposto` e
  `MontadorDaConferenciaExposta` (o campo de colapso); `ControladorDeAnalises`
  (grava quem executou); nos testes, `RepresentacaoNuncaOmiteTest` (o argumento
  novo nos três construtores).
- Etapa 9: `js/telas/achados.js` e `js/telas/naoavaliados.js` (grupos passam a
  nascer abertos); `tecnica.html` (folha de estilo nova).
- Etapa 11: `index.html`, `js/conferencia/app.js`, `api.js`, `roteador.js`, e as
  telas `resultado.js`, `nota.js`, `lote.js`, `produto.js`, `historico.js` e
  `enviar.js`.
- Etapa 12: `ControladorDeCorrecoes` (grava quem executou),
  `RepositorioDeUsuariosNoBanco` (quem executou conta como registro atribuído),
  `MatrizDePermissoes`; nos testes, `PermissoesPorEndpointTest`.

**Verificação.** `mvn test`: **1016 testes, nenhuma falha, nenhum pulado**, com o
Docker de pé. São os 981 da Etapa 12 mais 35 novos, entre eles
`RegraDeColapsoTest`, `ColapsoSoPeloServidorTest`, `HistoricoPelaApiTest`,
`HistoricoSemSomaProibidaTest`, `SituacaoMaisGravePresenteTest` e
`AcuraciaPelaApiTest`.

Na direção contrária, seis sabotagens no servidor, todas acusadas: regra de
colapso recolhendo não concluído; agrupamento recolhido com pendência; situação
do histórico pela maioria; filtro de situação ignorado no banco; medição usando
qualquer carga mais recente; resposta aceitando recolher não concluído. **Uma
passou na primeira rodada** — a da maioria —, porque as análises do teste por
HTTP têm um produto só, e nelas "maioria" e "mais grave" coincidem. O caso de seis
sem divergência e quatro não concluídos foi acrescentado, e a sabotagem passou a
ser acusada.

**Interface:** sonda em Node contra um DOM mínimo, 35 conferências, e sete
sabotagens numa cópia das páginas, todas acusadas, cada uma com as 35
conferências rodadas: colapso recolhendo tudo; resumo fixo removido de cima das
abas; aba de não concluídos sem a contagem; histórico sem mandar o filtro ao
servidor; medição mandando a carga de agora e não a que a tela mostrou; passo
recolhido à força; filtro rotulado só "Situação". A sonda não é versionada.

---

### Revisão de 27/09/2026 — o resultado da análise em visual de painel

A pedido do usuário, a tela de resultado foi enxugada. Os arquivos:
`css/resultado.css`, que é novo, e `css/painel.css`, que o `index.html` passou a
carregar. As duas folhas são ligadas pelas classes `na-resultado` e
`visual-painel` no `<body>`, na rota do resultado. O que mudou:

- **Procedência da carga numa linha** (`procedenciaCompacta`, em `pecas.js`).
  - O pedido era esconder o card sempre atrás de um ícone. Isso foi posto ao
    usuário e recusado, porque um resultado sobre carga fictícia ficaria sem
    aviso visível. **Quem decide continua sendo o servidor, por `exigeAviso`.**
  - Sem aviso: uma linha discreta com o rótulo e a versão.
  - Com aviso: um alerta compacto, à vista, com as tabelas de demonstração
    escritas.
  - Nos dois casos, a explicação inteira fica atrás do botão "i".
  - `faixaDeNatureza` continua inteira, e em uso nas telas de base e de carga.
- **Quadros de estados compactos** (`quadroDeEstados(..., { compacto: true })`).
  - Cada célula leva só o número, a marca e o rótulo.
  - As quatro explicações vão juntas para um botão "i" ao lado do título. A nota
    de como as verificações foram contadas vai junto.
  - As quatro células continuam idênticas entre si, inclusive os zeros.
  - Sem a opção, a função se comporta como antes, e é assim que a tela de
    produto a usa.
- **O resumo fixo continua cumprindo esta decisão:** a pendência, os ilegíveis e
  as versões estão escritos, em letra menor, e nenhum deles atrás de botão. O
  pedido admitia tooltip para a pendência, e ela ficou à vista, porque "não foi
  possível concluir" não pode sair do resumo.
- **O aviso de uso saiu da aba Resumo e foi para o pé da página**, fora das abas.
  Com isso, passou a aparecer qualquer que seja a aba aberta.
  - O cinza pedido para ele, `#94a3b8`, dava 2,4 de contraste. Ficou `#5b6778`.
- **Documento em grade.**
  - Sem chave exposta, a célula diz "não exposta nesta instalação", e o motivo
    do servidor fica atrás de um "?". O pedido sugeria "chave mascarada
    (proteção de CNPJ)", mas a chave não é mascarada: ela não é exposta, por
    configuração.
  - O pseudônimo sai inteiro, com quebra.
- **Abas no mesmo desenho das outras telas**, em `#1a365d`.

**Verificação:** feita por sonda em Node contra um DOM mínimo, com dado
fictício, e rodada duas vezes: com carga fictícia e com carga normativa. Foram
14 conferências passando nas duas rodadas. Quatro sabotagens acusadas:
- procedência ignorando `exigeAviso`;
- células com zero sumindo;
- pendência saindo do resumo fixo;
- aviso de uso sumindo.

**Não foi aberta em navegador.** Os testes do Maven não foram rodados.

### Revisão de 27/09/2026 — o detalhe do produto consolidado

A pedido do usuário, para cortar a rolagem do detalhe do produto. O estilo mora
em `css/produto.css`, ligado pela classe `na-produto`, que reaproveita
`visual-painel` e `na-resultado`. **Duas partes do pedido foram postas ao
usuário e recusadas por ele:**

- **Apagar as listas "O que o documento declarou" e "Tratamento indicado".** A
  tabela "lado a lado" tem 5 linhas; a lista do declarado tem os campos todos
  (NCM, CFOP, cClassTrib, bases, valores), e o tratamento é a resposta que a
  tela existe para dar (D012). No lugar disso:
  - o declarado e a comparação viraram **uma tabela só**, com todos os campos
    declarados e o indicado ao lado, casado pelo nome do campo. Onde a carga não
    indica nada, a célula diz "sem indicação na carga". Linha da comparação que
    não casar com nenhum campo declarado entra no fim da tabela, para nada sumir;
  - o tratamento continua **inteiro**, num cartão em grade.
- **Destacar em vermelho as linhas em que declarado e indicado diferem.** A
  tabela continua sem veredito: nenhuma linha é marcada.

O resto do pedido entrou:

- **Topo:** o título, a situação como pílula, a procedência compacta (com
  alerta quando o servidor exige) e o documento em grade. As explicações da
  situação foram para um "i".
- **Cartões de verificações do produto:** saíram. O estado de cada verificação
  continua escrito, regra a regra, em "Por que este resultado".
- **Descrições:** ficaram em dois cartões.
  - Sem descrição, aparece "não disponível", com o motivo do servidor atrás de
    um "?".
  - O pedido era "descrição anonimizada". A descrição não é anonimizada, é não
    exposta, por configuração — então a palavra não foi usada.
- **Regras:**
  - a frase "versão da regra: …" virou uma etiqueta curta (`v1.0.0`) no
    cabeçalho de cada regra. Sem versão, o motivo continua escrito;
  - o esperado ausente virou um traço, com texto para leitor de tela e o motivo
    atrás de um "?";
  - o valor em risco foi para o topo da explicação, em destaque, na cor de
    texto de divergência;
  - o colapso continua decidido pelo servidor (`recolhidaPorPadrao`).
- **Aviso de uso:** no pé da página, em `#5b6778`. O `#94a3b8` pedido reprovava
  em contraste.

**Verificação:** feita por sonda em Node contra um DOM mínimo, com dado fictício.
Foram 22 conferências passando. Cinco sabotagens acusadas:
- a linha que só existe na comparação sumindo;
- a tabela marcando linha diferente;
- o colapso forçado aberto;
- a base normativa saindo da tela;
- o motivo do esperado virando texto à vista.

**Não foi aberta em navegador.** Os testes do Maven não foram rodados.

**Acréscimo do mesmo dia.** A pedido do usuário, saíram do detalhe dois textos
que o servidor manda para regra concluída sem violação: a conta da derivação e o
motivo de não haver versão (o banco não grava avaliação conforme). A regra sai
só com o cabeçalho — o nome e a pílula "sem divergência identificada" — e sem
área recolhível vazia. **O motivo da pendência continua**, porque é ele que diz
por que a regra não concluiu. A sonda passou a 25 conferências, e acusou as duas
sabotagens: a conta da derivação voltando, e a pendência sumindo junto.

### Revisão de 05/10/2026 — a tabela de análises anteriores cabendo na tela

A pedido do usuário, com autorização prévia para `js/conferencia/telas/historico.js`
e `css/historico.css` (seção 6), e cláusula de emenda nos dois.

**O diagnóstico do pedido não se confirmou, e a medição está aqui.** O pedido
era envolver a tabela num contêiner com `overflow-x:auto`, porque "a rolagem
horizontal é da página". O contêiner já existia (`.hist-rolagem`). Medido no
Edge sem interface, com 20 linhas fictícias e os textos reais do servidor:

| janela | tabela | área visível | página rola para o lado | barra horizontal em y |
|---|---|---|---|---|
| 1340×675 | 1852px | 1130px | não | 1432 |
| 1894×987 | 1852px | 1130px | não | 1414 |

A página não rolava: quem rolava era o contêiner, e a barra dele ficava no fim
das vinte linhas, abaixo da tela. A coluna Regras tinha 524px — o que a
alargava era a tolerância da R05, e não a versão — e a coluna Catálogo tinha
370px.

**Decisões do usuário:**

- a etiqueta de natureza do catálogo (D021) **saiu da linha**. Um ícone com
  `title="Procedência não declarada"` fixo foi posto ao usuário e não entrou,
  porque a etiqueta tem três rótulos — dados de demonstração, parcialmente
  fictício, procedência não declarada —, e o fixo seria falso nos dois primeiros;
- a tolerância da R05 (D023) **saiu da linha**; a coluna Regras mostra só a
  versão do conjunto;
- a área de rolagem ganhou altura máxima ligada à janela
  (`max(320px, 100vh − 180px)`), com o cabeçalho preso no topo, para a barra
  horizontal ficar à vista;
- versão do catálogo, versão das regras e executor ganharam largura máxima com
  reticências, e o texto inteiro no `title`; padding horizontal das células de
  14px para 8px; as quatro contagens com `gap` de 4px.

**O que isto desfaz, e o que não desfaz.** A D021 e a D023 levaram natureza e
tolerância à tabela do histórico, e esta tabela deixou de escrevê-las.
`RespostaDoHistorico` não mudou: continua exigindo as duas no construtor de cada
linha, e a API continua mandando. O resultado da análise, aberto pela linha,
continua mostrando a procedência e a tolerância.

**Verificação**, com a mesma sonda depois da mudança, e a versão do catálogo e
o executor propositalmente longos:

| janela | tabela | área visível | barra horizontal em y | barra visível |
|---|---|---|---|---|
| 1340×675 | 1202px | 1115px | 577 | sim |
| 1894×987 | 1202px | 1115px | 871 | sim |
| 998×675 | 1202px | 918px | 577 | sim |

Colunas no pior caso: Catálogo 196px (cortada), Regras 63px, Executada por
266px. **Sabotagem acusada:** sem a altura máxima, a barra volta para y=1106
numa janela de 675. **No pior caso a tabela ainda passa 87px da área**, e a
barra aparece, agora à vista. Com nomes curtos (catálogo `exemplos-2026.10`,
executor `Ana Ficticia (aficticia)`), a tabela mede 1115px em 1115px e não há
barra. A sonda não é
versionada; os testes do Maven não foram rodados, porque nenhum deles olha estes
dois arquivos.

---

## D015 — A R07 segue o critério da R02, e o grupo de redução passa a ser lido

**Data:** 03/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

> **Emenda a D004**, no ponto em que ela decidiu, para a R02, que "coluna
> preenchida em branco é indistinguível de coluna que ninguém preencheu" e que
> essa dúvida fica `NAO_AVALIADO`. A D004 não disse nada sobre a R07, e o código
> da R07 fez o oposto. A partir daqui **a R07 segue o mesmo critério da R02**.
>
> **Não emenda:** D002, D003, D005, D006 e D008. A D002 só perde a contagem que
> ela mesma dá dos argumentos de `ItemDocumento` — "quinze" passou a vinte e
> um —, e a decisão continua: sem construtor abreviado e sem valor padrão. A versão da R07 sobe, e por isso a
> D006 vale inteira: apontamento e tratativa da R07 `1.0.0` não se confundem com
> os da `1.1.0`.

### Contexto

A revisão adversarial de 03/10/2026 encontrou o quarto defeito da família que o
projeto já conhecia: **conclusão sem ter contra o que conferir**.

- `RegraCamposObrigatoriosPreenchidos` (R07 `1.0.0`) respondia `CONFORME` quando
  a lista `camposObrigatoriosCondicionados` do catálogo vinha vazia.
- `LinhaCsv.lista` fazia da célula em branco uma lista vazia, e descartava em
  silêncio o elemento vazio entre `|`.
- No catálogo de `exemplos/catalogo`, **120 de 161 códigos** tinham a célula em
  branco e caíam em `CONFORME`. Os outros 41 trazem nomes crus de indicador do
  ERP, que o vocabulário `CampoDoItem` não reconhece, e caíam em `NAO_AVALIADO`.
  Com esse catálogo, a R07 **não tinha como produzir apontamento**.
- A nota do corpus de avaliação feita para a R07 saía, na conferência, "Sem
  divergência identificada" nas sete regras, com o passo da R07 dizendo
  "Campos exigidos pelo cClassTrib vieram preenchidos", enquanto o bloco de
  tratamento da mesma resposta dizia que "a carga não lista campo que passe a
  ser exigido".

A D004 tinha resolvido essa mesma ambiguidade para a lista de CSTs da R02, no
sentido oposto. Nenhum documento registrava por que a R07 ficou diferente: não
foi decisão, foi lacuna.

A revisão encontrou também um segundo defeito na mesma regra, mais fundo: **o
grupo `gRed` do leiaute — `pRedAliq` e `pAliqEfet`, em `gIBSUF`, `gIBSMun` e
`gCBS` — não era lido em lugar nenhum fora das classes geradas.** A R07 só sabe
cobrar o que chega ao `ItemDocumento`, então uma nota sem o grupo de redução era
indetectável **mesmo com um catálogo perfeito**: a regra nunca pôde produzir
esse apontamento.

### Decisão

**A leitura do CSV distingue três estados**, no molde que a D2 já deu a
`anexosAdmitidos`:

| No arquivo | No domínio | R07 |
|---|---|---|
| coluna ausente | carga recusada, como sempre foi | — |
| célula em branco | `Optional.empty()` — não declarado | `NAO_AVALIADO`, com motivo |
| `NENHUM` | `Optional.of(List.of())` — declarou que não exige campo | `CONFORME` |
| `nome1\|nome2` | `Optional.of(lista)` | confere cada campo |

`NENHUM` misturado com nome, nome vazio entre `\|`, nome com espaço em volta e
nome repetido **recusam a linha**, com arquivo, linha, coluna e valor, e a carga
inteira cai, como na Etapa 12. A coluna deixou de passar por `LinhaCsv.lista`,
que não foi alterado: os CSTs compatíveis continuam lidos como antes.

**A R07 só conclui `CONFORME` quando o catálogo afirmou algo sobre aquele
código** — uma lista de nomes, ou `NENHUM`. Silêncio da fonte é pendência.

**`ClassificacaoTributaria.camposObrigatoriosCondicionados` passou de `List` a
`Optional<List>`.** Os construtores antigos, que recebem `List`, continuam
existindo e leem **lista vazia como "não declarado"** — decisão do usuário, por
ser o lado seguro. Fixture de teste que queira dizer "nenhum campo exigido"
passa a declarar isso pelo construtor canônico.

**Persistência.** A tabela filha `classificacao_tributaria_campo_obrigatorio`
não distingue os dois casos: célula em branco e `NENHUM` ficam, ambos, sem
linha. A `V17` cria a coluna anulável `campos_obrigatorios_declarados`, sem
INSERT nem UPDATE: `TRUE` é declaração, `FALSE` é célula em branco, `NULL` é
carga anterior. Na leitura de carga anterior, lista com nomes é declaração; lista
vazia fica "não declarado", porque ali branco e `NENHUM` eram a mesma coisa.

**Exposição.** A tela escrevia "a carga não lista campo que passe a ser exigido
por este cClassTrib" para os dois casos. Agora há dois motivos: um para
`NENHUM` declarado, e outro dizendo que a célula veio em branco e que isso não é
"nenhum campo exigido".

#### O grupo `gRed` passa a chegar ao item

A lista foi posta ao usuário antes de qualquer linha e confirmada por ele: os
**seis campos folha do grupo `gRed`**, tirados do XSD versionado
(`DFeTiposBasicos_v1.00.xsd`, tipo `TRed`, linhas 977 e 982), dentro dos três
grupos de tributo de `TCIBS`. Dentro de um `gRed` presente as duas folhas são
obrigatórias no XSD, então a presença de `pRedAliq` equivale à presença do grupo.

| No leiaute | No `ItemDocumento` e no `CampoDoItem` |
|---|---|
| `gIBSUF/gRed/pRedAliq` e `pAliqEfet` | `reducaoAliquotaIbsUf`, `aliquotaEfetivaIbsUf` |
| `gIBSMun/gRed/pRedAliq` e `pAliqEfet` | `reducaoAliquotaIbsMunicipal`, `aliquotaEfetivaIbsMunicipal` |
| `gCBS/gRed/pRedAliq` e `pAliqEfet` | `reducaoAliquotaCbs`, `aliquotaEfetivaCbs` |

- `NormalizadorDocumento` lê o grupo; grupo ausente é `Optional.empty()`, nunca
  zero, e a escala declarada é preservada, como em todo valor (D002, D005).
- `ItemDocumento` ganhou os seis componentes, e `CampoDoItem` os seis nomes. A
  R07 **só os cobra quando o catálogo os lista** — que campos um código exige é
  conteúdo normativo, e entra pela carga (seção 5). O sistema não deduz "redução
  diferente de zero, logo `gRed` exigido": isso seria regra normativa escrita em
  código.
- `V18` cria as seis colunas em `item_documento`, `numeric` sem precisão e
  anuláveis, sem INSERT nem UPDATE; `ItemDocumentoEntidade` e
  `MapeadorDeDocumento` acompanham.
- **Os seis campos ficam fora do `HashDoItem`**, por decisão do usuário: nenhum
  hash já gravado muda, e nenhuma tratativa reabre por isso. O custo é que dois
  itens que só diferem no `gRed` têm a mesma identidade de apontamento.

**Fora desta decisão, de propósito:** o XSD tem outros grupos que ele mesmo diz
serem "informados conforme indicador no cClassTrib" — `gEstornoCred`,
`gCredPresOper`, `gCredPresIBSZFM` —, além de `gTribRegular`, `gIBSCBSMono`,
`gDif` e `gDevTrib`. Os nomes crus do CSV de exemplos (`INDGTRIBREGULAR`,
`INDCREDPRES`, `INDMONO`, `INDGESTORNOCRED`…) **parecem** corresponder a esses
grupos, mas a correspondência é decisão de quem monta o catálogo, e a R07 ainda
só sabe cobrar campo, não grupo. Fica registrado como próximo passo, não como
ponto de extensão.

#### Versões

R07 `1.0.0` → `1.1.0`; `ConjuntoRegras.VERSAO_PADRAO` `2026.3` → `2026.4`, **num
salto só** para os dois defeitos, por decisão do usuário. As outras seis regras
não mudaram.

### Consequência

- **Com `exemplos/catalogo`, a R07 passa a dar `NAO_AVALIADO` nos 161 códigos.**
  O arquivo de exemplos **não foi alterado**: marcar `NENHUM` nos 120 códigos em
  branco seria afirmar, sobre a norma, que eles não exigem campo algum, e isso é
  decisão de quem monta o CSV (CLAUDE.md, seção 5).
- **Cargas gravadas antes da `V17`** ficam com a coluna nula. Os códigos que lá
  tinham lista vazia passam a "não declarado"; só uma carga nova resolve, como
  na `natureza`, na `tributacaoIntegral` e nos `anexosAdmitidos`.
- **A nota do corpus feita para a R07 continua sem apontamento**, agora como
  `NAO_AVALIADO` e não como `CONFORME`: o código dela tem a célula em branco no
  catálogo de exemplos. Ela só gera apontamento quando a carga listar, para esse
  código, os campos do grupo de redução.
- **Teste obrigatório.** `CaminhoDaR07` enumera os nove caminhos da regra, cada
  um dizendo se o catálogo afirmou algo e qual resultado deve dar.
  `R07NaoConcluiSemDeclaracaoTest` percorre o enum e os três valores de
  `ResultadoAvaliacao`; `R07SemDivergenciaSoComDeclaracaoTest` percorre os
  quatro valores de `EstadoDeConferencia`. Os dois conferem também que todo
  caminho foi exercitado e que os resultados possíveis aparecem — sem isso, um
  caminho a menos passaria em silêncio.

### Verificação

- **Testes escritos antes da correção.** `R07NaoConcluiSemDeclaracaoTest` e
  `R07SemDivergenciaSoComDeclaracaoTest`, rodados contra a R07 com o
  comportamento antigo (`orElse(List.of())`): **5 de 7 vermelhos**. Os dois que
  passaram são os que conferem a cobertura do próprio enum.
- **Testes escritos antes da leitura do `gRed`.** `ReducaoDeAliquotaNoItemTest`,
  contra o normalizador ainda sem ler o grupo: **2 de 4 vermelhos**. Os dois que
  passaram afirmam ausência, que o normalizador provisório já cumpria.
- **Suíte:** **1102 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé. Eram 1070 na revisão de 02/10/2026 e 1016 na Etapa 13. A
  máquina tem pouca memória livre e a JVM do Maven caiu três vezes por falta de
  memória nativa; as rodadas valem com `MAVEN_OPTS="-Xmx768m"`.
- **Testes novos:** `CaminhoDaR07` (enum de apoio),
  `R07NaoConcluiSemDeclaracaoTest`, `R07SemDivergenciaSoComDeclaracaoTest`,
  `CamposObrigatoriosNoCsvTest`, `CamposObrigatoriosNoBancoTest`,
  `MotivoDosCamposExpostoTest`, `ReducaoDeAliquotaNoItemTest` e
  `ReducaoDeAliquotaNoBancoTest`, e a nota fictícia
  `src/test/resources/documentos-reducao/nfe-reducao-distinta-por-tributo.xml`,
  derivada de `nfe-item-completo-reducao-60.xml` com um valor diferente por
  tributo. Ela fica fora de `documentos/` porque tem a mesma chave fictícia das
  notas de lá, e os testes que leem aquela pasta inteira a contariam como
  documento repetido.
- **Testes que mudaram por causa da correção**, e só eles:
  `RegraCamposObrigatoriosPreenchidosTest.deveDizerConformeQuandoOCatalogoNaoCondicionaCampoAlgum`
  afirmava exatamente o defeito — virou `deveDizerConformeQuandoOCatalogoDeclaraQueNaoExigeCampoAlgum`,
  com um teste novo para o caso não declarado; `QuatroCenariosDeConferenciaTest`
  (duas fixtures) e `HistoricoPelaApiTest` (uma) passaram a declarar `NENHUM`
  explicitamente; três ajustes de compilação sem mudança de intenção
  (`RegistrosDoCatalogoTest`, `RegraTratamentoDeAnexoNaoAproveitadoTest`,
  `ImportadorClassificacaoTributariaCsvTest`); e o literal de versão em
  `ConjuntoRegrasTest`, `AcuraciaPelaApiTest`, `HistoricoPelaApiTest` e
  `ComandoAvaliarAcuraciaTest`. Pelo `gRed`, os dez construtores de
  `ItemDocumento` em teste ganharam os seis argumentos — `Optional.empty()`,
  exceto o que monta "todos os campos" em `CampoAusenteNaoEZeroTest`, que
  recebe o mesmo valor dos demais — e `ConstrutorDeItem` ganhou seis métodos.
- **Sabotagens**, cada uma aplicada, rodada contra os mesmos 34 testes e
  desfeita, com o arquivo conferido idêntico por resumo SHA-256:

| Sabotagem | Resultado |
|---|---|
| controle: troca inofensiva de comentário | 0 de 34 |
| R07 volta a concluir com campos não declarados | 6 de 34 falham |
| importador lê célula em branco como `NENHUM` | 2 de 34 |
| importador descarta nome vazio em silêncio | 2 de 34 |
| mapeador lê toda lista gravada como declarada | 2 de 34 |
| construtor antigo lê lista vazia como `NENHUM` | 1 de 34 |
| API escreve o mesmo motivo para branco e `NENHUM` | 1 de 34 |

  E do `gRed`, contra 19 testes (20 na segunda rodada):

| Sabotagem | Resultado |
|---|---|
| controle: troca inofensiva de comentário | 0 de 19 |
| normalizador lê a alíquota efetiva da CBS no grupo do IBS UF | 2 de 19 falham |
| normalizador lê a **redução** da CBS no grupo do IBS UF | **0 de 19 — passou** |
| normalizador grava zero quando o grupo não veio | 1 de 19 |
| mapeador não grava a redução da CBS | 1 de 19 |
| vocabulário da R07 aponta `reducaoAliquotaCbs` para a alíquota da CBS | 1 de 19 |

  A que passou não era furo do código: a nota de teste declara `60.0000` nos
  três grupos, e o teste não tinha como distinguir de que grupo o valor vinha. A
  nota `nfe-reducao-distinta-por-tributo.xml` e o teste
  `cadaTributoDeveSerLidoDoProprioGrupo` entraram por causa disso, e na segunda
  rodada a mesma sabotagem foi acusada (1 de 20).

- **Acurácia.** Jar do estado `2026.3` (guardado antes de empacotar) contra o
  jar `2026.4`, cada um com o próprio banco descartável e `exemplos/catalogo`
  importado pelo próprio importador:
  - com `exemplos/gabarito-exemplo.csv` sobre `src/test/resources/documentos`,
    as métricas por regra e consolidadas são **idênticas** — nenhum rótulo é da
    R07;
  - rotulando **todas** as células como `CONFORME`, para o harness classificar
    cada uma: nos documentos de teste, 42 células, **nenhuma muda** (a R07 já
    dava `NAO_AVALIADO` ali, porque os códigos fictícios não estão no catálogo);
    no corpus de avaliação, 63 células, **8 mudam, todas da R07, de `CONFORME`
    para `NAO_AVALIADO`**. As outras seis regras não mudam em célula nenhuma.
  - com o jar final, já lendo o `gRed`, as métricas do gabarito de exemplo
    continuam idênticas, e o corpus fica com 8 apontamentos, 43 conformes e 12
    não avaliados. O grupo chega ao banco em 5 dos 9 itens do corpus — os que o
    declaram.

---

## D016 — A R05 lê a cobertura das classificações antes de assumir "sem redução"

**Data:** 03/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

> **Emenda a decisão D4** do usuário, registrada na revisão de 30/09 a
> 02/10/2026 da D012, no trecho "código fora do catálogo: fator 1, como na
> 1.0.0". Esse trecho continua valendo **dentro da cobertura declarada** da
> tabela de classificações; fora dela, deixa de valer.
>
> **Aplica a D004**, sem emendá-la: silêncio do catálogo só é resposta dentro da
> cobertura declarada da carga.

### Contexto

A R05 1.1.0 passou a ler a redução de alíquota na tabela de classificações.
Quando o código não estava na tabela, ela seguia a D4 — "fator 1, como na
1.0.0" — e recalculava o valor com a alíquota cheia. O construtor nem recebia a
cobertura da tabela, então a regra não tinha como distinguir "o catálogo foi
carregado para esta data e não traz o código" de "a tabela não cobre esta data".

"Como na 1.0.0" não era neutro: a 1.0.0 **não lia** a classificação, e por isso
não dependia dela. A 1.1.0 passou a depender, e transformava o silêncio da tabela
em "redução zero" — a mesma dedução de ausência para zero que a revisão de
30/09/2026 corrigiu na R04, aqui produzindo veredito nos dois sentidos.

A revisão adversarial de 03/10/2026 demonstrou o efeito sobre o corpus de
avaliação, com uma carga cuja tabela de classificações não cobria a data das
notas e cuja tabela de alíquotas tinha as linhas de `exemplos/catalogo`: todas as
outras regras responderam `NAO_AVALIADO`, e a R05 concluiu nas nove notas —
**3 `CONFORME` e 6 `ACHADO` GRAVE**, com valor em risco calculado sobre notas
corretas, inclusive a de controle (7,111).

### Decisão

**A R05 recebe a cobertura declarada da tabela de classificações**, e
`ConjuntoRegras.padrao` entrega a ela a cobertura dessa tabela — não a de NCM nem
a de itens de anexo.

| Situação do item | R05 1.2.0 |
|---|---|
| não declarou `cClassTrib` | fator 1 (D4): a tabela não é consultada |
| código **encontrado** na tabela, vigente na data | aplica a redução declarada, como na 1.1.0 — registro encontrado é declaração, e a cobertura só decide o que fazer com o silêncio |
| código **ausente**, tabela **cobre** a data | fator 1 (D4): o catálogo foi carregado para a data e não traz o código |
| código **ausente**, tabela **não cobre** a data | **`NAO_AVALIADO`**, com motivo que cita o código, o período coberto e a data de emissão |

Redução em branco e CST de redução de base continuam como a D4 e a D5 decidiram.

**Versões:** R05 `1.1.0` → `1.2.0`; `ConjuntoRegras.VERSAO_PADRAO` `2026.4` →
`2026.5`. O veredito muda, e é para isso que a versão existe: pela D006, um
apontamento da R05 `1.1.0` não se confunde com um da `1.2.0`, e tratativa dada
contra o critério antigo não silencia o novo.

### Consequência

- Carga cuja tabela de classificações não cobre a data das notas não produz mais
  veredito da R05 para código que ela não traz. Com `exemplos/catalogo`, que cobre
  a partir de 2026-01-01, nada muda nas notas do corpus nem nas de teste.
- O motivo da pendência diz qual período a tabela cobre e qual é a data da nota,
  para quem lê saber que o que falta é carga, e não dado no documento.
- Fica de pé o que a D4 decidiu para dentro da cobertura, e isso continua sendo
  uma escolha: código que o catálogo não conhece é tratado como sem redução. A R01
  aponta o mesmo item como código inexistente, e é ela que diz o que está errado.

### Verificação

- **Testes escritos antes da correção.** `R05ForaDaCoberturaTest`, contra a R05
  já recebendo a cobertura mas ainda sem usá-la: **3 de 6 vermelhos**. Os três
  que passaram afirmam o que não deveria mudar — a D4 dentro da cobertura, o
  registro encontrado aplicando a redução, e o item sem `cClassTrib`.
  O teste reproduz o cenário da revisão com nove notas fictícias — valores
  reduzidos (a de controle), cheios e misturados — e exige `NAO_AVALIADO` em
  todas, sem apontamento, e o motivo escrito.
- `R05NoConjuntoPadraoTest` confere que o conjunto padrão entrega à R05 a
  cobertura das classificações, com coberturas divergentes entre as tabelas para
  uma troca aparecer.
- **Testes de etapas anteriores alterados:** os construtores da R05 em
  `RegraValorDeTributoConfereTest` (três) e em
  `RegraValorDeTributoConfereComReducaoTest` (um) passaram a receber a cobertura
  de `CenarioFicticio.coberturaTotal()`; e os literais de versão em
  `ConjuntoRegrasTest`, `AcuraciaPelaApiTest`, `HistoricoPelaApiTest` e
  `ComandoAvaliarAcuraciaTest`. Nenhum desfecho esperado mudou.
- **Suíte:** **1110 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé e `MAVEN_OPTS="-Xmx768m"`.
- **Sabotagens**, cada uma aplicada, rodada contra os mesmos 26 testes e
  desfeita, com o arquivo conferido idêntico por SHA-256:

| Sabotagem | Resultado |
|---|---|
| controle: troca inofensiva de comentário | 0 de 26 |
| R05 volta a assumir redução zero fora da cobertura | 4 de 26 falham |
| R05 inverte a cobertura | 7 de 26 |
| R05 deixa pendente também item sem `cClassTrib` | 1 de 26 |
| R05 deixa pendente também código encontrado fora da cobertura | 1 de 26 |
| conjunto entrega à R05 a cobertura de NCM | 2 de 26 |

- **O cenário da revisão, com o jar e o corpus de avaliação** — carga com a
  tabela de classificações cobrindo a partir de 2030 e as alíquotas de
  `exemplos/catalogo`:
  - jar `2026.4`: R05 com 3 `CONFORME` e 6 `ACHADO` GRAVE, a nota de controle
    entre eles, com valor em risco 7,111;
  - jar `2026.5`: R05 com **9 `NAO_AVALIADO`** e nenhum apontamento; a auditoria
    inteira fica em 63 de 63 não avaliadas, nenhuma conforme.
- **Acurácia.** Jar `2026.4` contra `2026.5`, cada um com o próprio banco e
  `exemplos/catalogo`: métricas do `gabarito-exemplo.csv` **idênticas** — os 6
  rótulos da R05 dão VP1 FP2 FN0 VN0 NAv3 nos dois —; e, rotulando todas as
  células, **nenhuma muda** nos documentos de teste (42) nem no corpus (63),
  porque ali a tabela de classificações cobre a data.

---

## D017 — A redução de base sai do código e entra no catálogo

**Data:** 03/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

> **Emenda as decisões D1 e D5** do usuário, registradas na revisão de 30/09 a
> 02/10/2026 da D012. A D1 dizia que a redução de base "não é representada" no
> catálogo e que "não foi criada coluna de incidência"; a D5 a reconhecia pelos
> CST `222` e `210`. Os dois pontos mudam: a incidência passa a ser declarada pela
> carga. O resto da D1 — as colunas de redução do catálogo são redução de
> **alíquota** — continua, e é ele que decide o caso não declarado.

### Contexto

A R05 1.1.0 trazia, em `dominio/regras/RegraValorDeTributoConfere`, a lista
`Set.of("210", "222")`: códigos de CST que a decisão D5 tratou como de redução de
base. Isso contrariava a seção 5 do CLAUDE.md e a afirmação da emenda da Etapa 11
de que nenhum código normativo foi escrito em código — e nenhum teste acusava.

Além de estar no lugar errado, a lista estava errada nos dois sentidos. A própria
D1 dizia que o CST `210` só é de redução de base **com o indicador
`ind_RedutorBC`** do cClassTrib: a lista deixava pendente todo código que admitia
o `210`, com ou sem o indicador, e não reconhecia redução de base em código que
não estivesse nela. E consultava o CST declarado no item, que é assunto da R02.
Sobre o que a redução incide é propriedade do **cClassTrib**, e o cClassTrib vem
do catálogo.

### Decisão

**Coluna opcional `reducaoIncideSobre`** em `classificacao-tributaria.csv`, com
`ALIQUOTA` ou `BASE`, proposta ao usuário e aprovada antes de ser criada:

| Célula | R05 1.3.0 |
|---|---|
| `ALIQUOTA` | a redução declarada incide sobre a alíquota, como na 1.1.0 |
| `BASE`, com redução diferente de zero | `NAO_AVALIADO`, com o motivo da D5: redução de base não suportada |
| em branco, ou coluna ausente | **lida como alíquota**, pela D1 — decisão do usuário |
| qualquer outro valor | linha recusada, com linha, coluna e valor |

Com redução zero a coluna não é consultada. É uma coluna só para CBS e IBS, como
a redução. A R05 deixou de consultar CST — do item e da lista de compatíveis.

- No domínio, `IncidenciaDaReducao` (`ALIQUOTA`, `BASE`) é categoria, como
  `TipoDeCodigoDoAnexo`, e não código da norma. `ClassificacaoTributaria` ganhou o
  componente `Optional<IncidenciaDaReducao> reducaoIncideSobre`; o construtor da
  aridade anterior o deixa não declarado.
- `V19` cria `classificacao_tributaria.reducao_incide_sobre`, anulável, com
  restrição de forma (`ALIQUOTA` ou `BASE`), sem INSERT nem UPDATE.
- **Versões:** R05 `1.2.0` → `1.3.0`; conjunto `2026.5` → `2026.6`.

**Não ficou exceção nenhuma em código.** E para a próxima não entrar em silêncio,
`NenhumCodigoNormativoEmCodigoTest` varre `src/main/java` atrás de literal de
texto só com dígitos no comprimento de CST (3), de cClassTrib (6) ou de NCM (8),
descontados os comentários. O único literal permitido é o `"100"` da R05, que é a
divisão por cem do percentual (D004), registrado no próprio teste com o motivo.
Exceção futura que se prove inevitável entra ali, com o motivo, e numa ADR.

### Consequência

- **Nada muda com `exemplos/catalogo` nem com as cargas atuais**: sem a coluna,
  a redução é lida como alíquota, e o único código de exemplos com CST `222` tem
  redução zero — a lista antiga nunca disparava ali.
- O que **deixa de existir** é a pendência automática por CST: uma carga antiga
  com código de redução de base e redução diferente de zero, que a 1.2.0 deixava
  pendente pelo CST, passa a ter a redução aplicada sobre a alíquota até a carga
  declarar `BASE`. É o custo da decisão de ler o não declarado pela D1.
- Quais códigos têm redução de base é decisão de quem monta o CSV, a partir da
  norma. O sistema não preenche nada.

### Verificação

- **Guarda escrito antes da mudança:** `NenhumCodigoNormativoEmCodigoTest`
  acusou exatamente `"210"` e `"222"` na R05, e nada mais em `src/main/java`.
  Depois da mudança, verde. Tem dois testes de apoio: um exige que a varredura
  encontre os arquivos de produção, e o outro, que o padrão acuse literal e cale
  comentário.
- **`R05IncidenciaDaReducaoTest`, escrito antes:** 1 de 4 vermelho contra a R05
  1.2.0 — `BASE` declarada dava `CONFORME`. Os outros três afirmam o que não
  muda: alíquota declarada, não declarado lido como alíquota, e redução zero.
- **Testes de etapas anteriores alterados:** em
  `RegraValorDeTributoConfereComReducaoTest`, o teste da D5 usava o CST `222` —
  código real — no item e no catálogo; passou a declarar `BASE` no catálogo e
  virou `naoDeveAvaliarReducaoDeBaseComReducaoDiferenteDeZero`. E os literais de
  versão em `ConjuntoRegrasTest`, `AcuraciaPelaApiTest`, `HistoricoPelaApiTest` e
  `ComandoAvaliarAcuraciaTest`.
- Novos `ReducaoIncideSobreNoCsvTest` e `ReducaoIncideSobreNoBancoTest`.
- **Suíte:** **1130 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé.
- **Sabotagens**, contra os mesmos 21 testes, cada arquivo restaurado e conferido
  por SHA-256: controle 0 de 21; R05 ignorando `BASE`, 2; R05 lendo não declarado
  como `BASE`, 4; R05 voltando a trazer um CST em código, 1 (o guarda); importador
  lendo branco como `ALIQUOTA`, 1; importador aceitando minúsculas, 1; mapeador
  sem gravar a incidência, 1. Todas acusadas.
- **Acurácia.** Jar `2026.5` contra `2026.6`, cada um com o próprio banco e
  `exemplos/catalogo`: métricas do `gabarito-exemplo.csv` idênticas, e nenhuma
  célula muda nos documentos de teste nem no corpus de avaliação.

---

## D018 — As falhas de leitura são gravadas junto da execução, e leitura não registrada não é zero

**Data:** 04/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

> **Emenda a D006, a D007 e a D012.** A D006 dizia que o `auditar` grava
> "documentos, itens, apontamentos e o recibo da execução"; passa a gravar também
> os arquivos que não leu. A D007 definia três abas e a identificação da planilha;
> passam a ser quatro abas, e a identificação ganha uma linha. A D012 dizia que o
> arquivo ilegível "fica em `falha_de_leitura_da_execucao` (V7) e é contado à
> parte" — isso só valia para a análise da interface, e a tabela vazia era lida
> como zero.

### Contexto

Demonstrado na revisão, com três arquivos, dois corrompidos, auditados pelo
comando `auditar`: a CLI imprimia "2 arquivo(s) não puderam ser lidos", a tabela
`falha_de_leitura_da_execucao` ficava com 0 linhas, a API respondia
`arquivosIlegiveis: 0` e "Nenhum arquivo deixou de ser lido", e a planilha dizia
"Documentos auditados 1" sem mencionar os outros dois.

Eram dois defeitos. O `ServicoDeAuditoria` não recebe as falhas, e o `auditar`
só as imprimia: quem gravava a tabela era o `ServicoDeAnalise`, da interface. E o
`MontadorDeRecibo` derivava o texto da tabela, sem distinguir "nenhuma linha
porque nada falhou" de "nenhuma linha porque ninguém gravou".

### Decisão

1. **O `auditar` grava as falhas, pelo mesmo registro da interface.**
   `ComandoAuditar` recebe `RegistroDoAcervoDaAnalise` e, logo depois da
   auditoria, registra os arquivos que ela não leu, convertidos por
   `OrigemDeArquivoIlegivel` — sem a pasta e sem o CNPJ no nome, como na
   interface. A lista de itens vai vazia: a CLI continua sem gravar os itens
   lidos, como antes. O registro de falhas da fonte da CLI é do processo, e o
   comando registra só o trecho acrescentado pela própria auditoria.

2. **Marca de leitura registrada (V20).** `leitura_da_execucao` guarda, por
   execução, quantos arquivos falharam. É gravada na mesma transação das falhas,
   pelo `AcervoDaAnaliseNoBanco`. A consulta passou a devolver
   `Optional<List<ArquivoIlegivel>>`:

   | Estado no banco | Resposta |
   |---|---|
   | marca, com tantas linhas quantas ela diz | a lista (vazia, se nada falhou) |
   | marca que não bate com as linhas | recusa: o banco foi mexido por fora |
   | sem marca, com item ou falha gravados | a lista — análise da interface anterior à V20, que gravava itens e falhas numa transação só |
   | sem marca e sem linha nenhuma | **vazio: a leitura não foi registrada** |

   A migration cria a tabela vazia, sem INSERT nem UPDATE. As execuções antigas
   do `auditar` ficam "não registradas", que é o que elas são.

3. **"Nenhum arquivo deixou de ser lido" só com a leitura registrada e a lista
   vazia.** No recibo da API, `arquivosIlegiveis` passou de `int` para `Integer`:
   sem a leitura registrada, ele e `arquivosQueNaoForamLidos` vêm `null`, com
   `motivoDosArquivosIlegiveisAusentes` dizendo por quê — o par da D009, exigido
   no construtor nas duas direções. O texto de `comoFoiALeitura` passou a ter os
   casos "não registrada", que dizem o que não se sabe.

4. **Planilha.** A identificação ganhou a linha "Arquivos que não puderam ser
   lidos", logo abaixo de "Itens auditados", e a planilha ganhou a quarta aba,
   **Não lidos**, com arquivo, tipo de erro e motivo. Sem a leitura registrada, as
   duas escrevem "(não registrado: …)" em vez de zero. O papel de trabalho recebe
   os arquivos por `ConsultaDosArquivosNaoLidos`, porta funcional nova em
   `aplicacao/papeldetrabalho/`.

5. **Tela.** O bloco de leitura e o resumo fixo escrevem o motivo quando a
   contagem vem nula. Antes, `inteiro(null)` escreveria "0".

**Arquivo ilegível continua nunca contado como nota sem divergência.** Nada
disso o põe numa contagem de estado: a conferência conta produtos de
`item_da_execucao`, e o ilegível não é produto.

### Consequência

- **Não muda nada no motor**, em `dominio/` nem nas versões de regra e de
  conjunto. A acurácia da Etapa 7 não é afetada.
- **A execução da CLI continua sem os itens lidos.** A conferência dela, aberta
  pelo histórico, lista os ilegíveis e os documentos lidos, mas nenhum produto —
  como antes desta decisão. Gravar os itens pela CLI gravaria também a descrição
  do produto, e isso não foi pedido.
- **A gravação das falhas pela CLI é uma transação depois da execução, não a
  mesma.** Se ela falhar, o comando termina em erro e a execução fica sem marca —
  "não registrada", e não "zero". A marca é o que torna essa falha honesta.
- **A visão técnica não mostra os ilegíveis**: os quatro GET da Etapa 8 não os
  expõem, como antes.

### Verificação

- **`FalhasDeLeituraNosPontosDeSaidaTest`, escrito antes da correção**, com o
  cenário demonstrado: o `auditar` de verdade, por Spring e PostgreSQL em
  contêiner, sobre uma nota boa e dois arquivos corrompidos, conferindo a CLI, a
  tabela, a API e a planilha. Contra o código anterior, 3 de 3 vermelhos, pelos
  motivos demonstrados: banco com 0 linhas, planilha sem a linha, e execução sem
  registro respondendo `arquivosIlegiveis: 0`. Mais três casos: a segunda
  auditoria no mesmo processo não herda as falhas da primeira; análise da
  interface anterior à V20 continua com a lista; marca que não bate com as linhas
  é recusada — seis ao todo. O lote limpo, único que diz "Nenhum arquivo deixou
  de ser lido", é um dos três primeiros. *(Corrigido em 04/10/2026: este texto
  dizia "mais quatro casos", contando o lote limpo duas vezes.)*
- **Testes de etapas anteriores alterados:** `ExportadorXlsxTest` (as linhas
  abaixo de "Itens auditados" desceram uma, e são quatro abas), as duas asserções
  de `AcervoDaAnaliseNoBancoTest` sobre o tipo novo, e o argumento novo do
  montador em `MontadorDePapelDeTrabalhoTest`, `MontadorDeRespostasTest` e
  `NenhumIdentificadorEmTextoClaroNaExportacaoTest`.
- **Sonda em Node** contra DOM mínimo, nos três casos (dois ilegíveis, zero, não
  registrada): 7 de 7 com a tela nova; contra os dois módulos anteriores, 4
  falhas, todas no caso não registrado — escreviam "0". A sonda não é versionada.
- **Suíte:** **1137 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé.
- **Sabotagens**, contra os mesmos 33 testes, cada arquivo restaurado e conferido
  por SHA-256: controle 0 de 33; CLI sem registrar as falhas, 4; CLI registrando
  as falhas do processo inteiro, 2; acervo sem gravar a marca, 4; acervo lendo
  ausência de marca como zero, 1; acervo aceitando marca divergente, 1; acervo
  sem reconhecer a análise anterior à V20, 1; recibo tratando não registrada como
  zero, 1; planilha escrevendo 0 sem a leitura registrada, 2; montador da planilha
  sem consultar os não lidos, 2. Todas acusadas.

---

## D019 — Documento repetido no lote: a cópia idêntica conta uma vez, e a divergente não entra

**Data:** 04/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

> **Emenda a D005, a D006 e a D018.** A leitura do lote da D005 entregava um
> documento por arquivo legível; passa a entregar um por chave de acesso. A D006
> grava documento, item e apontamento pela identidade natural, e é por isso que
> duas cópias no mesmo lote viravam uma linha no banco e duas no recibo. A D018
> criou a marca de leitura; ela ganha a contagem dos repetidos.

### Contexto

O caso comum: o `-nfe.xml` e o `-procNFe.xml` da mesma nota no mesmo lote. O
`LeitorLote` entregava os dois documentos, o motor avaliava os dois, e:

- a CLI gravava o recibo com 2 documentos e 2 apontamentos GRAVE, e o banco
  ficava com 1 documento e 1 apontamento, pela identidade natural da D006;
- a exportação dessa execução caía com rastro de pilha, na conferência de
  `PapelDeTrabalho` entre o recibo e as linhas;
- com conteúdo diferente, o item do primeiro arquivo era sobrescrito em silêncio
  por `RepositorioDaAuditoriaNoBanco`;
- a web recusava o lote — demonstrado na revisão, pela restrição
  `item_da_execucao_unico` —, com a mensagem
  "outra pessoa mudou o mesmo dado ao mesmo tempo. Recarregue e tente de novo" —
  falsa, e mandando repetir uma ação que nunca funcionaria.

No cenário do teste apareceu ainda uma quarta forma: com uma avaliação não
concluída no item, o `auditar` caía na restrição `avaliacao_nao_concluida_unica`.

### Decisão

1. **A leitura do lote resolve as cópias, antes do motor.**
   `FonteDeLoteNoSistemaDeArquivos` agrupa os documentos pela chave de acesso, na
   ordem de leitura. Para isso, `LeitorLote` ganhou `lerComOrigem`, que entrega
   cada documento com o arquivo de onde veio (`DocumentoLido`); `ler` continua
   igual. Os dois caminhos — CLI e interface — usam essa fonte, e a medição de
   acurácia também.
2. **Conteúdo igual é cópia.** "Igual" é o que o sistema leu: o `Documento`
   normalizado e todos os itens, em ordem de número, comparados por valor — o
   envelope de autorização não entra no que é lido. A cópia é descartada e
   contada em `LoteDeDocumentos.documentosRepetidosDescartados`.
3. **Conteúdo diferente é conflito, e nenhum dos arquivos entra.** Cada um é
   registrado como falha, com o tipo `ChaveDeAcessoComConteudoDivergente` e o
   motivo, e aparece onde aparecem os arquivos que ficaram de fora (D018): CLI,
   `falha_de_leitura_da_execucao`, API, planilha e tela. Ficar com um seria
   decidir qual é o verdadeiro. O motivo não cita os outros arquivos, porque o
   nome deles pode trazer a chave e, dentro dela, o CNPJ do emitente.
4. **Guarda estrutural.** `LoteDeDocumentos` recusa dois documentos com a mesma
   chave, venha de qual fonte vier, com mensagem que não repete a chave.
5. **A contagem dos repetidos é registrada e exposta.** Migration `V21`: coluna
   anulável `leitura_da_execucao.documentos_duplicados`, sem INSERT nem UPDATE;
   nula é "não registrado". `ResultadoDaAuditoria` a carrega,
   `RegistroDoAcervoDaAnalise.registrar` a recebe, e ela aparece na CLI
   ("repetidos descartados"), no recibo (`documentosRepetidosDescartados`, nulo
   com `motivoDosRepetidosAusentes`), no texto de `comoFoiALeitura`, na planilha
   (linha "Documentos repetidos descartados", abaixo dos arquivos não lidos) e no
   bloco de leitura da tela.
6. **A mensagem do banco diz a causa.** `CausaDaRecusaDoBanco`, nova, lê só a
   primeira linha da mensagem do PostgreSQL — as seguintes trazem o valor gravado.
   Violação de restrição nomeia a restrição e diz que repetir o pedido terá o
   mesmo resultado; recusa de gatilho repassa o texto do gatilho, que é do
   sistema; o que não se identifica diz isso. Só a concorrência de verdade
   (`ConcurrencyFailureException`) manda recarregar e tentar de novo. Código HTTP
   e código do erro não mudaram.
7. **Exportação não termina em rastro de pilha.** `LinhaDeComando` passou a
   tratar `PapelDeTrabalhoInvalido` como recusa com mensagem própria, código de
   saída 2. A mensagem do desencontro entre recibo e linhas passou a dizer a causa
   conhecida: execução gravada antes desta decisão a partir de lote com documento
   repetido. A planilha dessa execução continua não sendo emitida, porque teria de
   afirmar um dos dois números; auditar o lote de novo resolve.

### Consequência

- **Nada sob `dominio/` mudou**, nenhuma regra, nenhuma versão.
- **Acurácia:** a deduplicação também vale para o `avaliar-acuracia`, que usa a
  mesma fonte. O corpus de avaliação tem 9 notas e 9 chaves distintas, e
  `exemplos/` não tem XML: nas duas, a deduplicação não altera nada, e as
  métricas da Etapa 7 não mudam.
- **Lote com o mesmo documento em versões diferentes** passa a ter os dois de
  fora. Quem quiser auditar um deles precisa tirar o outro do lote: o sistema não
  escolhe.
- **Execuções gravadas antes desta decisão com cópia no lote** continuam
  inconsistentes no banco: o recibo conta as cópias. A exportação delas é
  recusada com a causa, e o histórico continua mostrando o recibo como foi
  gravado.
- **Entre lotes diferentes, nada mudou:** reprocessar uma nota com conteúdo novo
  sobrescreve o item, como a D006 decidiu.

### Verificação

- **`DocumentoRepetidoNoLoteTest`, escrito antes da correção**, por Spring e
  PostgreSQL em contêiner: duplicata idêntica pela CLI (contada uma vez, reportada
  na CLI, no banco e na API), exportação da execução resultante (sai inteira, com
  o total de apontamentos igual ao gravado), duplicata divergente (nenhuma versão
  gravada, as duas listadas com o motivo na API e na planilha), o mesmo par num
  `.zip` pela web (aceito), e exportação de execução inconsistente pela
  `LinhaDeComando` (código 2, mensagem, sem exceção). Contra o código anterior,
  5 de 5 vermelhos.
- **`MensagemDoBancoDizACausaTest`**: restrição nomeada sem o detalhe, gatilho
  repassado sem o contexto do PL/pgSQL, concorrência mandando tentar de novo.
- **`LoteDeDocumentosSemChaveRepetidaTest`**: a guarda estrutural, inclusive que
  a mensagem não repete a chave.
- **Testes de etapas anteriores alterados:** `ExportadorXlsxTest` (as linhas
  abaixo dos arquivos não lidos desceram mais uma, e um caso novo), as chamadas de
  `registrar` em `AcervoDaAnaliseNoBancoTest`, e o argumento novo do montador em
  `MontadorDePapelDeTrabalhoTest`, `MontadorDeRespostasTest` e
  `NenhumIdentificadorEmTextoClaroNaExportacaoTest`.
- **Suíte:** **1150 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé.
- **Sabotagens:** doze, contra os mesmos 39 testes, cada arquivo restaurado e
  conferido por SHA-256: controle 0 de 39; fonte tratando conteúdo divergente
  como cópia (ficando com o primeiro), 1; fonte sem registrar o conflito, 1; fonte
  sem contar as cópias, 3; fonte sem deduplicar, 11; lote aceitando chave
  repetida, 2; serviço perdendo a contagem, 3; CLI registrando zero, 2; acervo
  gravando zero, 3; recibo do histórico ignorando a contagem, 2; planilha
  escrevendo 0 sem registro, 1; CLI deixando a recusa do papel de trabalho subir
  com a pilha, 1; mensagem do banco voltando a mandar tentar de novo, 2. Todas
  acusadas.
- **Sonda em Node** da linha nova do bloco de leitura, nos três casos (1, 0 e
  nulo com motivo): 3 de 3 com a tela nova, 3 falhas com o `pecas.js` anterior.

---

## D020 — Histórico: contagem não medida é "não registrado", e nenhum filtro a exclui

**Data:** 04/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

> **Emenda a D014.** A D014 gravou, por execução, a contagem de produtos em cada
> estado e filtrou o histórico por ela. Para a execução que não registrou os itens
> lidos, a contagem gravada era quatro zeros — e zero não era a contagem: era a
> ausência dela.

### Contexto

O resumo do histórico conta produtos, e a lista de produtos sai de
`item_da_execucao`. O comando `auditar` não grava essa tabela. Para as execuções
dele, `AcervoDoHistoricoNoBanco` gravava zeros, a tela escrevia "Possível
divergência: 0", e o filtro `minimoDeDivergencias=1` excluiu, na demonstração do
usuário, uma execução com 15 apontamentos.

Havia duas saídas: calcular o resumo das execuções da CLI, ou mostrar "não
registrado" e não excluí-las por filtro. A segunda foi proposta, com o motivo, e
aprovada pelo usuário antes da implementação.

### Decisão

1. **Contagem medida.** A execução tem contagem medida quando gravou os itens que
   leu, ou quando não leu item nenhum — zero produtos ali é medição. A regra é
   uma expressão só, `AcervoDoHistoricoNoBanco.CONTAGEM_MEDIDA`, calculada na
   consulta.
2. **Sem medida, sem número.** A linha sai com a contagem e a situação nulas, e o
   motivo `LinhaDoHistorico.CONTAGEM_NAO_REGISTRADA` ao lado (`motivoDaContagemAusente`,
   `motivoDaSituacaoAusente`) — o par da D009. A tela escreve "não registrado", com
   o motivo no `title` e no leitor de tela.
3. **Nenhum filtro de situação ou de quantidade exclui a execução não medida**:
   excluir por um valor que não foi medido esconde resultado real. Vale para
   `situacaoMaisGrave`, `minimoDeDivergencias` e `maximoDeDivergencias`. A
   extensão ao filtro de situação foi confirmada pelo usuário.
4. **A tela diz por que elas estão ali.** A página traz
   `execucoesNaoMedidasNoResultado` e `explicacaoDasNaoMedidas`; com filtro de
   situação ou de quantidade ativo, a tela mostra quantas execuções não medidas
   estão incluídas e por quê — sem isso o resultado parece não corresponder ao
   filtro. Pedido do usuário.
5. **O banco deixa de gravar zeros.** Migration `V22`: as cinco contagens de
   `resumo_da_execucao` passam a aceitar nulo, sempre as cinco juntas, sem INSERT
   nem UPDATE. O resumo de execução não medida é gravado com nulos. Os resumos já
   gravados com zeros ficam no banco e não são lidos como contagem: a consulta
   decide pela expressão do item 1, e não pela coluna. Um resumo gravado sem
   contagem é substituído pelo medido se a execução passar a ter itens — o caso
   da análise da web lida pelo histórico antes de registrar o que leu. Nessa mesma
   janela, a linha medida ainda sem resumo sai com "contagem em cálculo", e não
   com zero.

### A assimetria entre web e CLI é escolha conhecida, não lacuna

A análise da web tem contagem por produto; a execução da CLI não. **Gravar os
itens lidos pela CLI ficou fora desta decisão, por escolha do usuário**, pelos
motivos:

- a segunda saída é necessária de qualquer forma: as execuções da CLI já gravadas
  não têm a lista de itens, e nenhuma gravação nova a reconstrói;
- gravar os itens arrasta a decisão sobre `xProd`, que tem controles próprios
  (D012) e está pendente na D018 — são assuntos com riscos diferentes;
- a CLI é o caminho da medição de acurácia, e estabilidade ali vale mais agora.

**O que resolveria:** o `auditar` registrar os itens lidos em `item_da_execucao`,
como a análise da web faz, com a decisão sobre a descrição do produto tomada
antes. As execuções da CLI feitas depois disso passariam a ter contagem; as
anteriores continuariam "não registrado".

### Consequência

- **Nada sob `dominio/` mudou**, nenhuma regra, nenhuma versão; acurácia intacta.
- A tela de resultado de uma execução da CLI, aberta pelo histórico, continua
  montando a conferência a partir de `item_da_execucao`, e mostra zero produtos.
  Isso não foi tratado aqui.
- Execução não medida aparece em todo filtro de situação e de quantidade. Quem
  quiser só as medidas não tem filtro para isso; não foi pedido.

### Verificação

- **`HistoricoComContagemNaoMedidaTest`, escrito antes da correção**, por HTTP e
  PostgreSQL em contêiner, com quatro execuções: web sem divergência, web com
  divergência, a do caminho do `auditar` com apontamentos, e uma que não leu item
  nenhum. Contra o código anterior, 6 de 7 vermelhos; o sétimo, do filtro
  máximo, passava por acaso, porque o zero gravado fazia a execução entrar em
  "máximo 0".
- **Testes de etapas anteriores alterados:** só o construtor de
  `PaginaDoHistorico` em `HistoricoSemSomaProibidaTest`. `HistoricoPelaApiTest`
  passou sem mudança.
- **Suíte:** **1157 testes, nenhuma falha, nenhum erro, nenhum pulado**, com o
  Docker de pé, compilada do zero.
- **Sabotagens:** oito, contra os mesmos 20 testes, cada arquivo restaurado e
  conferido por SHA-256, com `target/classes` apagado a cada rodada: controle 0
  de 20; toda execução tida como medida, 5; zero itens tido como não medido, 4;
  filtro de situação excluindo a não medida, 1; filtro mínimo, 1; filtro máximo,
  1; página dizendo zero não medidas, 3; leitura usando o resumo gravado em vez da
  medida, 1; serviço gravando zeros para a não medida, 1. Todas acusadas.
- **Sonda em Node** da tela do histórico: 7 de 7 com a tela nova — "não
  registrado" e os motivos na linha não medida, os números na medida, o aviso com
  filtro de situação e de mínimo, nenhum aviso sem filtro, página só de não
  medidas. Com a tela anterior, as 7 falham. A primeira rodada acusou a linha
  medida também na tela nova: era a sonda, que procurava os quatro números
  colados, e o selo escreve o rótulo entre eles.
- **Nota de método:** a primeira rodada destes testes deu 500 por "Unresolved
  compilation problem". O compilador do editor tinha gravado em `target/classes`
  uma classe com erro de sintaxe meu — um *text block* aberto com conteúdo na
  mesma linha —, mais nova que o fonte, e o Maven a tomou por atualizada. Daqui
  em diante, `target/classes` é apagado antes de cada rodada.

---

## D021 — A natureza do catálogo viaja até todo resultado, e a cobertura entra nela

**Data:** 04/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

> **Emenda a D012 e a D007.** A D012 criou a natureza por tabela e a faixa de
> procedência, exigida "em toda resposta de resultado" — o que valia para a
> interface de conferência, e não para a visão técnica, o histórico, a acurácia
> nem a planilha. E a D012 deixou o `cobertura.csv` sem natureza, de propósito: o
> comentário de `LeitorDeCatalogoEmCsvTest` registrava que ele "diz período e
> fonte, não conteúdo". Esta decisão reverte isso, a pedido do usuário. A D007
> definiu o leiaute da planilha; toda aba ganha a faixa na primeira linha.

### Contexto

Demonstrado na revisão:

- R01 e R06, de severidade CRÍTICA, citavam como fundamento "FONTE FICTICIA DE
  EXEMPLO v0.0" — a fonte que o `cobertura.csv` declara — sob a faixa "Catálogo
  normativo", sem aviso: as cinco tabelas de dados eram normativas, e a cobertura
  não entrava na conta;
- a planilha gerada com catálogo inteiramente fictício não tinha nenhuma
  ocorrência de "fictício", "natureza" ou "procedência";
- `RespostaDaExecucao` e `RespostaDeAchados`, da visão técnica, não carregavam a
  natureza.

### Decisão

1. **A cobertura entra na natureza.** O `cobertura.csv` passa a exigir a coluna
   `natureza`, com a mesma regra dos outros arquivos de dados: obrigatória, uma
   natureza por arquivo, recusa com linha e motivo. `NaturezaDaCarga` ganhou a
   tabela `COBERTURA`; a linha vai para `natureza_da_carga` como as outras, sem
   migration — a coluna `tabela` já é texto livre. Na edição de carga, um
   `cobertura.csv` novo traz a natureza dele.
2. **Cobertura sem natureza não é normativa.** As cargas gravadas antes desta
   decisão não têm a linha da cobertura. A cobertura sempre tem conteúdo, então
   isso é procedência não declarada: havendo tabela fictícia, a situação continua
   a fictícia; não havendo, deixa de ser "Catálogo normativo" e passa a
   "Procedência não declarada", pedindo reimportação. A faixa e a planilha listam
   as tabelas sem natureza declarada. Os textos das situações "inteiramente
   fictício" e "não declarada" foram ajustados para continuar verdadeiros nesse
   caso.
3. **A faixa vai a todo resultado da API.** `RespostaDaExecucao`,
   `RespostaDeAchados`, `RespostaDeNaoAvaliados`, cada `ExecucaoResumida` da
   lista, cada linha de `RespostaDoHistorico` e `RespostaDaAcuracia` passaram a
   trazer `natureza` (`FaixaDeNatureza`), exigida no construtor. A conferência já
   trazia. `FaixaDeNatureza` ganhou `tabelasSemNaturezaDeclarada`.
4. **A tela mostra.** As cinco telas da visão técnica desenham a faixa; o
   histórico marca a natureza ao lado da versão do catálogo, quando há aviso; a
   acurácia mostra a faixa no topo da medição; a faixa da conferência lista as
   tabelas sem natureza declarada.
5. **A planilha marca o catálogo fictício tão visivelmente quanto a tela.** Toda
   aba abre com a faixa na primeira linha, mesclada, com a situação em
   maiúsculas, a explicação, as tabelas fictícias, as sem natureza declarada e a
   versão da carga; com aviso, fundo amarelo e borda grossa. A faixa e o
   cabeçalho ficam fixos ao rolar. O Resumo traz "Natureza do catálogo" na
   identificação e, quando há, "Tabelas fictícias" e "Tabelas sem natureza
   declarada". Com catálogo normativo, nenhuma linha da planilha fala em fictício
   ou em demonstração. O papel de trabalho lê a natureza pela mesma porta da
   conferência.

### Consequência

- **Nada sob `dominio/` mudou**, nenhuma regra, nenhuma versão; acurácia intacta.
- **Os `cobertura.csv` existentes param de importar até ganharem a coluna.** É o
  mesmo custo que a D012 declarou para os outros quatro arquivos. O de
  `exemplos/catalogo` ganhou `FICTICIO`: duas das três linhas citam fonte
  fictícia, e um arquivo tem uma procedência só.
- **Cargas já gravadas** perdem o rótulo "Catálogo normativo" e passam a
  "Procedência não declarada", até serem reimportadas.
- **Mudou o leiaute da planilha**: toda aba tem uma linha a mais no topo, e quem
  lê a planilha por posição precisa saber disso.

### Verificação

- **`NaturezaNaPlanilhaTest`**: catálogo fictício marcado em toda aba, com
  destaque; normativo sem nenhuma ocorrência de "fictíci" nem de "demonstração";
  misto dizendo quais tabelas, inclusive a cobertura, e não listando as
  normativas; cobertura sem natureza saindo "Procedência não declarada".
- **`CoberturaNaNaturezaTest`**: cobertura fictícia com o resto normativo dá
  "parcialmente fictício" com `COBERTURA`; cobertura normativa dá normativo;
  `cobertura.csv` sem a coluna, ou com duas naturezas, é recusado; carga antiga
  sem a natureza da cobertura não é normativa.
- **`NaturezaNosPontosDeSaidaTest`**, por HTTP e PostgreSQL em contêiner, com o
  cenário demonstrado — tabelas normativas e cobertura fictícia: a natureza da
  cobertura gravada, e a faixa "parcialmente fictício" com `COBERTURA` na
  conferência, na execução, nos apontamentos, nos não avaliados, na lista de
  execuções, no histórico e na planilha.
- Estes testes foram escritos junto com a mudança, e não antes: as assinaturas
  novas não existiam. O vermelho foi verificado pelas sabotagens.
- **Testes de etapas anteriores alterados:** `ExportadorXlsxTest` (as posições
  desceram; um caso novo da faixa), `LeitorDeCatalogoEmCsvTest` (cinco tabelas
  fictícias, não quatro, e o comentário emendado), o `cobertura.csv` escrito em
  sete testes, e os construtores em `MontadorDePapelDeTrabalhoTest`,
  `MontadorDeRespostasTest`, `NenhumIdentificadorEmTextoClaroNaExportacaoTest` e
  `HistoricoSemSomaProibidaTest`.
- **Suíte:** **1172 testes, nenhuma falha, nenhum erro, nenhum pulado**,
  compilada do zero.
- **Sabotagens:** onze, contra os mesmos 36 testes, cada arquivo restaurado e conferido
  por SHA-256, com `target/classes` apagado a cada rodada: controle 0 de 36;
  cobertura fora das tabelas declaradas, 8; carga sem natureza da cobertura
  voltando a ser normativa, 2; leitor do CSV sem passar a natureza da cobertura,
  2; leitor aceitando cobertura sem natureza, 2; banco sem ler a natureza da
  cobertura, 4; visão técnica, histórico e planilha com natureza não declarada,
  1 cada; faixa só no Resumo, 4; faixa sem destaque, 1; linha de tabelas
  fictícias mesmo sem nenhuma, 9. Todas acusadas.
- **Sonda em Node** da tela: 13 de 13 com a tela nova, e as 13 falham com a
  anterior. Cinco delas — uma por tela técnica — conferem o código-fonte, e não o
  desenho: que a tela importa e chama a faixa com a natureza da resposta.
- **`exemplos/catalogo` importado pelo leitor real**: com a coluna nova, a carga de exemplo sai "parcialmente fictício" com `COBERTURA` listada; o `cobertura.csv` antigo, sem a coluna, é recusado com a mensagem de natureza obrigatória.

---

## D022 — Dispositivo e fonte sem nenhuma letra não identificam norma

**Data:** 04/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

### Contexto

No `exemplos/catalogo/classificacao-tributaria.csv`, o código `200025` trazia
`dispositivoLegal_ibs = 0` e `fonteNormativa_ibs = 0`. O importador aceitava, e
juntava os lados como "CBS: … | IBS: 0"; a tela mostrava um fundamento que não
identifica norma nenhuma. O mesmo arquivo tem outros problemas que **são de dado,
não de código**, e ficaram com o usuário: o cabeçalho declara a fonte como
consulta ao ERP e o `indicadorDeBeneficio` como campo calculado, e todas as
linhas, mesmo assim, declaram `NORMATIVO`; a redução do `200025` foi montada como
"o maior dos dois" ao preparar o CSV — o importador não escolhe: com
`reducao_cbs` e `reducao_ibs` diferentes, ele recusa a linha (revisão de
14/09/2026 na D012).

### Decisão

**Dispositivo legal e fonte normativa sem nenhuma letra são recusados**, com
linha, coluna e valor: `TextoQueIdentificaNorma`, nova, em
`infraestrutura/catalogo/`. Vale para:

- `dispositivoLegal` e `fonteNormativa` da classificação, na coluna única e em
  cada lado da forma por tributo, antes de juntar os dois;
- `fonteNormativa` de todos os outros arquivos — `registro-ncm.csv`,
  `item-anexo.csv`, `aliquota-vigente.csv`, `cobertura.csv` e
  `anexos-declarados.csv`.

**O critério é só de forma.** O sistema não sabe qual norma é a certa (CLAUDE.md,
seção 5) e não confere se um texto aceito corresponde a ela: "x" passa. O que ele
recusa é o que não pode ser norma alguma — um número ou um sinal solto.

### Consequência

- **Nada sob `dominio/` mudou**, nenhuma regra, nenhuma versão.
- **O `exemplos/catalogo` deixa de importar** até a linha 50 (`200025`) ser
  corrigida: o leitor real recusa `fonteNormativa_ibs = 0`. A mesma linha tem
  `dispositivoLegal_ibs = 0`, que não aparece na mensagem porque a linha para na
  primeira recusa.
- A correção do arquivo — natureza das linhas, separar `fonteNormativa` (a origem
  real, tabela do ERP com data) de `dispositivoLegal` (o que essa origem afirma
  sobre a norma), e a linha `200025` sem escolher por ela — é do usuário, à mão.

### Verificação

- **`ValorQueNaoIdentificaNormaTest`, escrito antes**, com valores fictícios:
  contra o código anterior, 6 de 7 vermelhos, e o controle — catálogo com texto
  em todos os campos — verde. O oitavo caso, a fonte dos anexos declarados,
  entrou depois, quando se viu que a conferência também valia ali.
- **Suíte:** **1180 testes, nenhuma falha, nenhum erro, nenhum pulado**,
  compilada do zero.
- **Sabotagens**, contra os mesmos 35 testes, cada arquivo restaurado e conferido
  por SHA-256: controle 0 de 35; fonte dos arquivos sem conferência, 2; lado do
  IBS sem conferência, 3; coluna única sem conferência, 1; anexos declarados sem
  conferência, 1; critério recusando só o zero literal, 1. Todas acusadas.
- **Leitor real sobre `exemplos/catalogo`:** uma recusa só, linha 50,
  `fonteNormativa_ibs = 0`.

## D023 — A tolerância da R05 é gravada com a execução, e o padrão é dito como padrão

**Data:** 04/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

### Contexto

A D006 decidiu que a tolerância de valor da R05 era configuração obrigatória,
sem padrão. Em 21/09/2026 (`192c802`) o `application.properties` passou a dizer
`auditoria.tolerancia-de-valor=${AUDITORIA_TOLERANCIA_DE_VALOR:0.01}`: um padrão
dentro do placeholder, com o comentário logo acima ainda dizendo "NAO TEM
PADRAO", e o README ainda dizendo que a aplicação parava na subida sem a
variável. E nada — banco, planilha, API, tela, relatório de acurácia — registrava
qual valor tinha valido numa execução. Duas execuções com tolerâncias diferentes
dão resultados diferentes na R05, e não havia como ver isso sem investigar o
ambiente de quem rodou.

### Decisão

**A tolerância usada é gravada junto da execução, com a origem**, e sai em toda
saída que mostra resultado da R05.

- **O padrão fica, e fica explícito.** O pedido admitia as duas saídas — voltar à
  D006 ou manter o padrão — desde que, mantido, o padrão aparecesse na saída. A
  tolerância é resolvida em `ConfiguracaoDaAuditoria.toleranciaDaExecucao`:
  `auditoria.tolerancia-de-valor` preenchida é `CONFIGURADA`; em branco, vale
  `auditoria.tolerancia-de-valor-padrao`, propriedade própria, e a origem é
  `PADRAO`; sem as duas, a subida é recusada. O placeholder deixou de esconder
  valor. `ToleranciaDaExecucao` (`aplicacao/auditoria/`) leva o valor e a
  `OrigemDaTolerancia`, e o texto por extenso é "0.01 (padrão do sistema; a
  instalação não configurou outra)" ou "… (configurada na instalação)".
- **Migration `V23`:** `execucao_auditoria` ganha `tolerancia_de_valor` e
  `origem_da_tolerancia`, anuláveis, nulas juntas, sem INSERT nem UPDATE.
  **Nula é "não registrada"**, e toda saída escreve isso, nunca um valor —
  execução anterior a esta decisão pode ter usado qualquer tolerância.
- **Onde aparece:** a CLI do `auditar` ("tolerância R05"); a planilha, na linha
  "Tolerância de valor (R05)" do Resumo, abaixo da versão das regras; o CSV de
  acurácia, em comentário, e a medição pela API — as métricas da R05 dependem
  dela; na API, o campo `toleranciaDeValor` (`ToleranciaExposta`: quantia como
  texto, origem, texto, ou o motivo da ausência — o par da D009) no recibo da
  análise, no detalhe do produto, na execução, na lista de execuções, nos
  apontamentos, nos não avaliados, no histórico e na acurácia, exigido no
  construtor de cada resposta; e nas telas correspondentes.
- **Execução-a-execução, não regra-a-regra.** A tolerância é propriedade da
  execução, e só a R05 a usa. Ela sai no cabeçalho de cada resultado, e não
  dentro do passo da R05: a camada de conferência continua sem nenhum ramo por
  identificador de regra (D012).

### Consequência

- **Nada sob `dominio/` mudou**, nenhuma regra, nenhuma versão. A acurácia da
  Etapa 7 continua correspondendo ao código: a tolerância com que ela foi medida
  não muda, e agora o relatório diz qual foi.
- Execuções anteriores ficam com "não registrada" para sempre; não há como
  reconstituir o valor.
- **Mudou código de etapas anteriores, a pedido, com cláusula de emenda:**
  `ServicoDeAuditoria` e `ResultadoDaAuditoria`, `ExecucaoAuditoriaEntidade` e
  `RepositorioDaAuditoriaNoBanco`, `ComandoAuditar`, `ConfiguracaoDaAuditoria` e
  o `application.properties` (Etapa 5); `PapelDeTrabalho`,
  `MontadorDePapelDeTrabalho` e `ExportadorXlsx` (Etapa 6);
  `ServicoDeAvaliacaoDeAcuracia`, `RelatorioDeAcuracia` e
  `EscritorDeRelatorioDeAcuraciaCsv` (Etapa 7); `RespostaDaExecucao`,
  `ExecucaoResumida`, `RespostaDeAchados`, `RespostaDeNaoAvaliados` e
  `MontadorDeRespostas` (Etapa 8); `comum.js`, `api.js` e as telas técnicas
  `achados`, `achado` e `naoavaliados` (Etapa 9); `ServicoDeAnalise`,
  `ReciboDaAnalise`, `MontadorDeRecibo`, `RespostaDoDetalhe`,
  `MontadorDaConferenciaExposta`, `conferencia/telas/resultado.js` e
  `conferencia/telas/produto.js` (Etapa 11); `RespostaDoHistorico`,
  `ControladorDoHistorico`, `RespostaDaAcuracia`, `ControladorDeAcuracia`,
  `conferencia/telas/historico.js` e `conferencia/telas/acuracia.js` (Etapa 13).
  Novos: `OrigemDaTolerancia`, `ToleranciaDaExecucao`,
  `ConsultaDaToleranciaDaExecucao`, `ToleranciaDaExecucaoNoBanco`,
  `ToleranciaExposta` e a `V23`.
- Nos testes, só construtores e posições: `MontadorDePapelDeTrabalhoTest`,
  `NenhumIdentificadorEmTextoClaroNaExportacaoTest`, `MontadorDeRespostasTest`,
  `HistoricoSemSomaProibidaTest`, `NaturezaNaPlanilhaTest` e, em
  `ExportadorXlsxTest`, as linhas do Resumo abaixo da versão das regras, que
  desceram uma, mais um caso novo ali, o da tolerância não registrada. Novos:
  `ToleranciaNosPontosDeSaidaTest`,
  `ToleranciaComOrigemTest`, `ToleranciaNaMedicaoTest` e
  `ToleranciaNoRelatorioDeAcuraciaTest`.
- **A D006 foi emendada**, com o texto original preservado, e o README deixou de
  afirmar "sem padrão" e "para na subida".

### Verificação

- **`ToleranciaNosPontosDeSaidaTest`, escrito antes**, com tolerância fictícia
  `0.07` configurada: CLI, banco, API da conferência, da visão técnica e do
  histórico, planilha, e a execução "antiga" com as colunas apagadas. Contra o
  código anterior, os cinco primeiros casos vermelhos. O sexto — detalhe do
  produto, apontamentos e não avaliados — entrou depois, quando a conferência
  mostrou que o detalhe do produto, onde o passo da R05 aparece, não dizia a
  tolerância.
- **Sabotagens sem contêiner**, cada arquivo restaurado e conferido por SHA-256,
  contra os mesmos 43 testes: controle 0; padrão dito como configurado, 2;
  medição sem anexar a tolerância, 2; CSV com "0" no lugar de "não registrada",
  1; CSV sem a origem, 1; planilha com valor no lugar de "não registrada", 1.
  Todas acusadas.
- **Interface**, por sonda em Node contra DOM mínimo: 16 conferências no código
  novo; contra o anterior, exatamente as 8 desta rodada falham (linha da
  tolerância, três telas técnicas, paginação, cabeçalho do produto). Uma
  conferência da sonda devolvia promessa, que é verdadeira sempre, e foi
  corrigida antes de valer.
- **Suíte:** 1196 testes, nenhuma falha, **177 pulados por falta de Docker**.
  **Ainda não rodaram**: os testes de contêiner, entre eles os seis de
  `ToleranciaNosPontosDeSaidaTest`, e as sabotagens que dependem do banco e da
  API — gravação, leitura, `ToleranciaExposta` e o detalhe do produto.

## D024 — Arquivo de texto abre por um ponto só, em UTF-8 estrito

**Data:** 04/10/2026 — correção pedida pelo usuário depois da revisão adversarial
**Status:** Aceita

### Contexto

`FontesDoCatalogo` (D013) tinha dois caminhos de abertura que só pareciam
iguais. A pasta, usada pelo `importar-catalogo`, abria com
`Files.newBufferedReader(arquivo, UTF_8)`, que falha diante de byte inválido; o
envio, usado pela tela de cargas, com `new InputStreamReader(bytes, UTF_8)`, que
troca o byte inválido por U+FFFD em silêncio. O mesmo `registro-ncm.csv` em
Windows-1252 era recusado pela CLI com `MalformedInputException: Input length =
1`, sem dizer o que se esperava, e aceito pela web, gravado como "DESCRI?O
FICT?CIA". Um dado que o arquivo não tinha entrava no catálogo. O gabarito tinha
o defeito de mensagem: fora de UTF-8, saía como `UncheckedIOException`.

### Decisão

**Um ponto único de abertura: `infraestrutura/csv/AberturaEmUtf8`.** Ele lê o
arquivo inteiro e o decodifica em UTF-8 com `REPORT` para entrada malformada e
para caractere não mapeável. Decodificar tudo antes de entregar o `Reader` é o
que permite dizer a linha exata: lendo aos pedaços, o erro aparece quando o
buffer é preenchido, linhas antes do byte culpado. Fora de UTF-8, a recusa sai
pelo `RecusaDeCsv` de quem chama — `ImportacaoDeCatalogoInvalida` no catálogo,
`GabaritoInvalido` no gabarito — com o nome do arquivo, a linha, o byte e a
frase "UTF-8, que é a codificação esperada". **Nunca há substituição.**

- A pasta e o envio de `FontesDoCatalogo` passam por ele, e com isso a edição de
  carga pela web também. Os cinco importadores, no `importar(Path)` que só os
  testes usam, e o `LeitorDeGabaritoCsv` também.
- No catálogo, a recusa entra nas `RecusasDaCarga`, como qualquer outro
  problema do arquivo: a carga é recusada inteira, com a lista, e os outros
  arquivos continuam conferidos. O `anexos-declarados.csv`, que é aberto também
  só para saber se veio, conta como vindo.
- **Não se tenta adivinhar a codificação** nem aceitar outra: um arquivo
  Windows-1252 composto só de ASCII é UTF-8 válido e entra; um com acento é
  recusado, e quem o montou salva de novo. Aceitar Windows-1252 seria escolher
  uma interpretação do dado pelo usuário.
- O BOM continua como estava: é UTF-8 válido, e a tolerância a ele ficou fora
  por decisão do usuário (revisão de 14/09/2026 na D012).

### Consequência

- **Nada sob `dominio/` mudou.**
- `AberturaUnicaDeTextoTest` varre `src/main/java` e quebra o build se aparecer
  `InputStreamReader` ou `newBufferedReader` fora de comentário e literal, com
  autoverificação do padrão e de que a varredura olhou os arquivos certos. Fora
  dele ficam o arquivo do sal (`Files.readString`, que já é estrito e não é dado
  do usuário) e o XML, cuja codificação é a declarada no próprio documento.
- **Mudou código de etapas anteriores, a pedido, com cláusula de emenda:**
  `FontesDoCatalogo` e `LeitorDeCatalogoEmCsv` (Etapa 12),
  `ImportadorClassificacaoTributariaCsv`, `ImportadorRegistroNcmCsv`,
  `ImportadorItemAnexoCsv`, `ImportadorAliquotaVigenteCsv` e
  `ImportadorAnexosDeclaradosCsv` (Etapa 2 e revisões) e `LeitorDeGabaritoCsv`
  (Etapa 7). Nenhum teste existente mudou.

### Verificação

- **Testes escritos antes**, com valores fictícios. Contra o código anterior,
  `CodificacaoInesperadaNoCatalogoTest` deu 5 de 6 vermelhos — a pasta com
  `MalformedInputException`, o envio e a edição sem exceção nenhuma — e o
  controle em UTF-8 verde; `CodificacaoInesperadaNoGabaritoTest`, 1 de 2, com o
  controle verde. O sétimo caso do catálogo, o `anexos-declarados.csv`, entrou
  depois, para cobrir a abertura que só confere presença.
- **Leitor real, pelos dois caminhos**, sobre o mesmo catálogo com o
  `registro-ncm.csv` em Windows-1252: a mesma mensagem, palavra por palavra —
  "O arquivo "registro-ncm.csv" não está em UTF-8, que é a codificação
  esperada: a linha 2 tem o byte 0xC7…" —, e 0xC7 é o "Ç" em Windows-1252.
- **Sabotagens**, cada arquivo restaurado e conferido por SHA-256, contra os
  mesmos 38 testes: controle 0; o envio voltando ao `InputStreamReader`, 6 (o
  guarda entre eles); a abertura trocando o byte em vez de recusar, 7; a linha
  contada a partir de zero, 4; a recusa não registrada na carga, 5; a presença
  do arquivo sem tratar a codificação, 1; o gabarito voltando ao
  `newBufferedReader`, 2. Todas acusadas.
- **Suíte:** 1210 testes, nenhuma falha, **179 pulados por falta de Docker**.
  **Ainda não rodou** o `CodificacaoPelaCliEPelaWebTest`, que compara a saída do
  `importar-catalogo` com a resposta do `POST /api/cargas` e confere que nada foi
  gravado.

## D025 — Planilha, evidência, resumo e lista: o que faltava ser dito

**Data:** 04/10/2026 — correções pedidas pelo usuário depois da revisão adversarial
**Status:** Aceita

### Contexto

A revisão de 04/10/2026 apontou cinco defeitos de código e cinco textos que
afirmavam coisa falsa sobre o sistema atual. O pedido era um commit por item.
**Não houve commit, por decisão do usuário:** desde o `fb613ed` havia 126 arquivos
modificados e 170 não rastreados — Etapas 12 e 13 e D013 a D024 —, e vários dos
arquivos destes itens já tinham mudança pendente, que iria junto do primeiro
commit que os tocasse.

### Decisão

**1. Valor em risco na escala declarada.** `Celulas.valorEmRisco` recebia o
`BigDecimal` e o convertia em `double` sem conferir, com o formato fixo
`#,##0.00`: `7,11100` aparecia `7,11`. A planilha é gravada em streaming (SXSSF),
e nesse modo o POI só grava número como `double`; o Excel também só guarda
`double`. Decisão do usuário: **número com a escala**. O `BigDecimal` chega até a
célula; o estilo é escolhido pela escala dele (`EstilosDaPlanilha.monetario(int)`,
um estilo por escala, criado uma vez); e, quando o `double` lido de volta não é o
mesmo número, o valor vai como **texto exato** (`toPlainString`), para a planilha
nunca arredondar em silêncio. Duas colunas, e texto sempre, foram consideradas e
recusadas.

**2. Ausência de tratativa escrita.** "Justificativa" e "Tratado em" saíam em
branco no apontamento sem tratativa, contra a D007. Hoje dizem "(sem tratativa)
ninguém registrou decisão sobre este apontamento nesta versão da regra" — "nesta
versão" porque `ABERTO` também é o apontamento reaberto pela mudança de versão.

**3. Evidência do lado da tabela em R01 e R06.** As duas regras montavam o lado da
tabela com valor encontrado vazio. Pelo contrato de `Evidencia`, vazio quer dizer
"o campo não veio na nota", e a planilha escrevia "(não informado)" sobre o código
que a nota informou. Não dava para corrigir só pela origem da evidência: a R07
também usa evidência de tabela com o encontrado vazio, e ali o vazio quer dizer
mesmo que o campo não veio. Decisão do usuário: **corrigir a regra, sem mudar a
versão**. R01 e R06 escrevem `Evidencia.NENHUM_REGISTRO_NA_TABELA` ("nenhum
registro com este código na tabela carregada"), como a R03 já escrevia "nenhum
anexo". O critério, a severidade e o desfecho não mudam; nenhuma tratativa reabre.

As execuções antigas têm o lado vazio no banco. A planilha as relê sem reconhecer
regra nenhuma: evidência de tabela com o encontrado vazio, **cujo campo a nota
informou** numa evidência do documento ao lado, recebe o mesmo texto. O caso da
R07 — campo que a nota não trouxe — continua vazio, e continua "(não informado)".
A API não relê: execução antiga continua saindo com `valorEncontrado` nulo no
lado da tabela.

**4. Resumo da importação.** `ResumoDaImportacao` contava quatro tabelas desde que
os anexos declarados viraram a quinta (revisão de 30/09 a 02/10/2026 na D012).
Passou a contá-los e a somá-los no total, na CLI ("anexos declarados: N") e na
resposta do `POST /api/cargas`. O detalhe da carga na tela (`EstadoDaCarga`,
`CargaExposta.ContagemExposta`) tem a mesma omissão e **não foi tocado**: conta
pelo banco, por outro caminho, e não estava no pedido.

**5. Elemento vazio na lista.** `LinhaCsv.lista` descartava em silêncio o elemento
vazio de `AAA||BBB`, `AAA|` ou `|AAA`, e a classificação aceitava menos CSTs do
que a pessoa escreveu. Hoje recusa a linha com a coluna e o valor, como as outras
colunas de lista já faziam desde a D015. Espaço em volta continua sendo tirado, e
célula em branco continua lista vazia. No catálogo de exemplos, nenhuma das 161
linhas tem elemento vazio.

**Textos.** Cada um com o original preservado e emenda datada:

- README, "Como usar": sem argumento a aplicação sobe o `servir` com o perfil
  `api` desde 21/09/2026 (`192c802`), e não lista os comandos; a lista sai com
  nome de comando não reconhecido. A "configuração obrigatória acima" já não para
  a subida por falta: o sal se resolve sozinho (D011), usuário, senha e tolerância
  têm padrão.
- README, "Banco de dados", e o comentário do `application.properties`: "sem valor
  padrão para usuário e senha" e "nunca do repositório" deixaram de valer em
  21/09/2026 (`192c802`), quando entrou `auditoria`/`auditoria` no placeholder. Os
  padrões não foram removidos: a emenda corrige o que o texto afirma.
- README, `servir`, e INTERFACE-WEB, "O que ela faz": tratar achado e importar
  catálogo existem pela web desde a Etapa 12, com autenticação (D013). No README,
  as seções "Como usar" e "API" já tinham emenda; a do `servir` tinha ficado para
  trás.
- INTERFACE-WEB, revisão de 12/09/2026: `comum.js` mudou em 27/09 e em 04/10,
  `telas/acuracia.js` em 27/09, e `api.js` em 04/10. `css/base.css`, `dom.js`,
  `roteador.js` e `decimal.js` não mudaram desde o `192c802`; antes dele não há
  histórico para conferir.
- README, "Dados de exemplo": existe `exemplos/` desde 03/09/2026, fora do Git, e
  cinco dos seis arquivos do catálogo de lá declaram `NORMATIVO`.
- `exemplos/catalogo/aliquota-vigente.csv`: o cabeçalho diz que os percentuais
  não são referência normativa, e as três linhas declaram `NORMATIVO`. **Não foi
  tocado**: qual dos dois é verdade é do usuário, e a correção é dele, à mão, como
  na D022.

### Consequência

- **Sob `dominio/` mudaram** `Evidencia` (a constante), `RegraClassificacaoTributariaExiste`
  e `RegraNcmExiste`, com cláusula de emenda e sem `import` de framework. As
  versões das regras e do conjunto não mudaram, e a acurácia da Etapa 7 não muda:
  ela mede desfecho, e o desfecho de R01 e R06 é o mesmo.
- **Mudou código de etapas anteriores, a pedido, com cláusula de emenda:**
  `Celulas`, `EstilosDaPlanilha`, `ExportadorXlsx` e `MontadorDePapelDeTrabalho`
  (Etapa 6), `ServicoDeImportacaoDeCatalogo` e `ComandoImportarCatalogo` (Etapa
  5), `ControladorDeCargas` (Etapa 12) e `LinhaCsv` (Etapas 2 e 7).
- Nenhum teste existente mudou. Novos: `ValorEmRiscoComEscalaTest`,
  `TratativaAusenteEscritaTest`, `EvidenciaDoLadoDaTabelaTest`,
  `EvidenciaDeTabelaGravadaVaziaTest`, `ResumoComAnexosDeclaradosTest`,
  `ElementoVazioNaListaTest` e o auxiliar `PlanilhaDeAchadosDeTeste`.

### Verificação

- **Testes escritos antes**, com valores fictícios, e vistos vermelhos pelo motivo
  certo contra o código anterior: o formato `#,##0.00` e `0.123` exibido `0.12`;
  justificativa e "Tratado em" em branco; o lado da tabela de R01 e R06 vazio, e o
  da execução antiga também; a lista sem exceção nenhuma. Os controles — achado
  tratado, campo que a nota não trouxe, versões de R01 e R06, lista bem formada —
  verdes antes e depois. Duas falhas da primeira rodada eram do teste, que passava
  um apontamento a um papel cuja execução conta dois (guarda da D019), e foram
  corrigidas no teste.
- **Sabotagens**, cada arquivo restaurado e conferido por SHA-256, contra os
  mesmos 61 testes: controle 0; valor sempre como `double`, 1; escala fixa em duas
  casas, 2; justificativa ausente em branco, 2; "Tratado em" ausente em branco, 2;
  R01 com o lado da tabela vazio, 1; R06 idem, 1; planilha sem reler a evidência
  antiga, 1; planilha relendo sem conferir o campo, 1 (o caso da R07); total sem
  os anexos, 2; CLI sem a linha dos anexos, 1; lista descartando vazio de novo, 3.
  Todas acusadas.
- **Suíte:** 1227 testes, nenhuma falha, **179 pulados por falta de Docker**,
  compilada do zero. Nenhum dos testes desta decisão depende de contêiner; os
  pulados são os mesmos de antes, entre eles os de ponta a ponta de D023 e D024.
