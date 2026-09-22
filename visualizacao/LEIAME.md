# visualizacao/ — figuras dos resultados para o TCC

`resultados.html` é uma página estática que lê os arquivos que o sistema já
exporta e desenha as figuras do capítulo de resultados. Abra com duplo clique.

Não é parte do sistema. Não há Java aqui, nada foi acrescentado ao `pom.xml`,
nenhuma dependência entrou no projeto e nenhum arquivo de `src/` foi tocado.

## Como usar

1. Abra `resultados.html` no navegador (duplo clique serve; não precisa de
   servidor, nem de rede, nem de nada instalado).
2. Escolha o papel de trabalho `.xlsx` do comando `exportar` — desenha as
   seções 1 a 4.
3. Escolha o relatório `.csv` do comando `avaliar-acuracia` — desenha a
   seção 5.
4. Em cada figura, **copiar SVG** ou **baixar .svg**. O Word e o LibreOffice
   importam SVG, então a figura entra no documento sem virar imagem borrada.
   **copiar tabela** copia a mesma informação em texto, para colar como tabela.

Tudo acontece dentro do navegador: nenhum byte sai da máquina, e a página
funciona igual sem internet, inclusive num computador que nunca viu o projeto.

## Impressão em preto e branco

A opção **Padrões de preenchimento** troca as cores chapadas por hachuras a 45°
e 135°, para os três estados continuarem distintos numa página impressa em preto
e branco. Ela entra sozinha quando você manda imprimir, e o SVG exportado sai no
modo que estiver ativo — se a figura vai para um TCC impresso em P&B, marque a
opção antes de exportar.

Independentemente disso, cor nunca é a única codificação: todo estado tem rótulo
escrito na legenda, todo valor aparece ao lado da barra, e toda figura tem uma
tabela equivalente atrás do botão **ver tabela**.

## O conforme é derivado, e a página diz isso

Nenhum arquivo exportado conta os conformes. O papel de trabalho conta
apontamentos e avaliações não concluídas; o total de avaliações não está escrito
em lugar nenhum. Como as figuras precisam dos três estados, o terceiro sai de:

    total de avaliações = itens auditados × regras do conjunto
    conformes           = total − achados − não avaliados

A conta aparece impressa embaixo da figura, com os dois fatores. Se você tiver o
número por outro caminho, escreva-o no campo **Total de avaliações** do topo da
página e a derivação passa a usá-lo. Se a subtração der negativo, a página
escreve `(não derivável)` e não desenha barra nenhuma — nunca inventa o número.

Quando o relatório de acurácia está carregado, a página compara o total com a
linha `avaliações produzidas pelo motor` do CSV e avisa se os dois discordarem.

## O que a página não lê, de propósito

- **A coluna Justificativa** da aba Achados. É texto livre digitado por pessoa e
  pode conter CNPJ ou razão social.
- **As chaves de acesso** que o CSV de acurácia traz em texto claro no cabeçalho
  de comentários, nos endereços que o motor não avaliou. Os dígitos da chave
  carregam o CNPJ do emitente. A página conta essas linhas e nunca as escreve.
- **Série, número, UF e data de emissão.** Existem na planilha, mas nenhuma
  seção desta página desce ao nível do documento: toda figura é agregada, então
  nada que vá para o TCC identifica uma operação.

Nada do que vem dos arquivos é interpretado como marcação: todo texto entra por
`textContent`, nunca por `innerHTML`.

## Arquivos de dado não são versionados

O `.gitignore` do repositório bloqueia `*.csv` e `*.xlsx`. Os arquivos que você
largar aqui para gerar as figuras ficam fora do Git, e é assim que deve ser.
Só `resultados.html` e este `LEIAME.md` são versionáveis.

## Como a planilha é lida, já que não há biblioteca

Um `.xlsx` é um ZIP com XML dentro. O navegador já sabe descompactar
(`DecompressionStream`) e já sabe ler XML (`DOMParser`), então a página percorre
o diretório central do ZIP e lê as abas direto — sem biblioteca, sem CDN, sem
instalação. Ler o arquivo original evita o caminho pelo "salvar como CSV" do
Excel, que muda separador, vírgula decimal e formato de data conforme a máquina.

A aba Resumo é lida por rótulo, não por índice de linha. Quando as abas de
detalhe e o Resumo discordam, a página mostra o Resumo e avisa da divergência.
