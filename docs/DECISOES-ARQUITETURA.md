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
  percebe.
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

---
