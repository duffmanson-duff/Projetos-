# Abridor — projeto Android nativo

App estilo 7-Zipper: explorador de arquivos que abre qualquer formato,
compacta/descompacta ZIP, compartilha, edita (arquivos de texto/código),
renomeia, move, copia e exclui.

## Como gerar o APK

1. Baixe e instale o **Android Studio** (gratuito, site oficial).
2. Abra o Android Studio → **Open** → selecione a pasta `AbridorApp` (a pasta
   raiz deste projeto, que contém `settings.gradle`).
3. **Importante — faça isso antes de mais nada:** abra o **Terminal** do
   Android Studio (aba embaixo da tela) e rode:
   ```
   gradle wrapper --gradle-version 8.4
   ```
   Isso gera os arquivos que faltam do "Gradle Wrapper" (`gradlew`,
   `gradlew.bat`, `gradle-wrapper.jar`) já fixados numa versão compatível
   com o projeto — sem isso, o Android Studio pode tentar usar uma versão
   de Gradle nova demais e a build falha com erros confusos.
4. Aguarde o Gradle sincronizar sozinho (baixa as dependências — precisa de
   internet).
5. No menu superior: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. Quando terminar, aparece um aviso "APK(s) generated successfully" —
   clique em **locate** para achar o arquivo `.apk` gerado em
   `app/build/outputs/apk/debug/`.
7. Copie esse `.apk` para o celular e instale (pode precisar permitir
   "instalar de fontes desconhecidas" no Android).

## O que já está pronto

- **Explorador de pastas** com navegação, breadcrumb do caminho atual
- **Player de vídeo embutido** (VideoView com controles) — toca dentro do próprio app
- **Player de áudio embutido com visualizador estilo Windows Media Player** — barras de
  espectro que reagem e "batem no compasso" da música em tempo real (usa a API
  `android.media.audiofx.Visualizer`). Toque no visualizador durante a reprodução
  para alternar entre 5 efeitos:
  1. **Barras** clássicas com marcador de pico
  2. **Círculo pulsante**
  3. **Corredor 3D** em perspectiva (usa `android.graphics.Camera`), com o
     histórico do som se afastando ao fundo
  4. **Alquimia** — bolhas orgânicas coloridas orbitando, que "explodem" pra
     fora quando o grave bate forte, com as cores passeando pelo arco-íris
  5. **Ondas** — faixas senoidais fluidas coloridas, uma pra cada faixa de frequência
- **Equalizador completo** — botão "EQ" no topo da tela de áudio abre um
  equalizador de verdade:
  - **8 bandas de frequência** ajustáveis (60Hz a 16kHz) em aparelhos com
    Android 9 ou mais novo — usa a API `DynamicsProcessing` pra ter mais
    controle fino do que o equalizador padrão do sistema, que a maioria dos
    celulares limita a só 5 bandas. Em aparelhos mais antigos, cai
    automaticamente pro equalizador nativo do celular (quantidade de bandas
    definida pelo fabricante)
  - **9 presets prontos** (Normal, Rock, Pop, Jazz, Clássica, Dance, Grave+,
    Agudo+, Voz)
  - **Reforço de graves** e **efeito surround**
  - **Realce de volume (loudness)** — ganho extra pra músicas gravadas baixas
  - **Balanço estéreo** (esquerda/direita) — corrige áudio desbalanceado ou fone
    com um lado mais fraco
  - **Limitador anti-distorção** — evita que o som estoure quando os outros
    efeitos estão altos (disponível a partir do Android 9)
- **Abrir qualquer outro tipo de arquivo**: delega para os apps já instalados no celular
  via `Intent.ACTION_VIEW` — é assim que se consegue abrir literalmente
  qualquer formato que não seja tratado internamente
- **ZIP**: compactar qualquer arquivo/pasta e descompactar direto na pasta atual
- **Compartilhar** (abre o menu de compartilhamento do Android)
- **Editor de texto embutido** para `.php .js .py .json .html .css .txt` etc,
  com botão salvar
- **Renomear, excluir, copiar, mover (recortar/colar), detalhes do arquivo**
- **Temas** — botão "TEMAS" no topo do explorador abre um seletor com 6 paletas
  de cores prontas (Âmbar Clássico, Verde Menta, Azul Ártico, Roxo Nebulosa,
  Vermelho Ferrugem, Grafite Mono). A escolha fica salva no aparelho e vale
  pra todas as telas do app: explorador, player de vídeo, player de áudio
  (inclusive o visualizador e o equalizador), o editor de texto e o editor de fotos
- **Editor de fotos completo** — toque numa imagem (.jpg, .png, .bmp, .webp) para
  abrir o editor embutido:
  - **Cortar** — arraste os cantos do retângulo pra ajustar
  - **Girar** (90°) e **Espelhar**
  - **Filtros prontos** — Original, P&B, Sépia, Vívido, Quente, Frio, Negativo, Vintage
  - **Ajustes com pré-visualização ao vivo** — brilho, contraste, saturação, temperatura
  - **Desenho livre** — com paleta de cores e espessura de traço ajustável
  - **Texto** — adiciona texto por cima da imagem
  - **Desfazer** — volta a última ação, com histórico de várias etapas
  - **Salvar** (gera um novo arquivo com sufixo "_editado", sem sobrescrever o
    original) e **Compartilhar** o resultado direto pra outros apps
- **Visual 3D/moderno** — ícones de pasta e de arquivos ZIP desenhados com
  gradiente de luz, aba e sombra suave (em vez de retângulo chapado), e os
  principais botões (play/pause, confirmar, fechar) com acabamento "glossy"
  em gradiente vertical, dando sensação de volume/relevo. Tudo respeita o
  tema de cores escolhido

## Sobre o visualizador de áudio

Na primeira música tocada, o Android vai pedir a permissão "Gravar áudio" — é
só para o efeito visual funcionar (o `Visualizer` do sistema precisa dela em
alguns aparelhos), o app não grava nem salva nada. Se você negar, a música
toca normalmente, só sem o efeito de barras.

## Próximos passos que você pode pedir para eu continuar

- Ícone do app em PNG de verdade (hoje é um vetor simples)
- Visualizador de imagem embutido no próprio app (hoje abre no visualizador padrão)
- Mini player de áudio persistente (continua tocando enquanto navega em outras pastas)
- Busca de arquivos por nome
- Favoritos / atalhos de pastas
- Suporte a RAR (formato fechado, precisa de biblioteca extra)

## Permissões

O app pede acesso "gerenciar todos os arquivos" (Android 11+), porque sem
isso não é possível navegar livremente pelo armazenamento — é a mesma
permissão que apps como o 7-Zipper e o Files by Google pedem.

## Erros corrigidos numa revisão de código (19/08)

Depois que você relatou erros ao gerar o APK, revisei o projeto inteiro
arquivo por arquivo (sem conseguir compilar de verdade neste ambiente, já
que não tenho o Android SDK aqui) e corrigi:

- **`context_menu.xml`** tinha o namespace do XML errado
  (`.../res/android` em vez de `.../apk/res/android`) — isso sozinho
  já quebrava a build.
- **3 telas** (`activity_main.xml`, `activity_audio_player.xml`,
  `activity_image_editor.xml`) usavam `app:title`/`app:titleTextColor`
  sem declarar o namespace `xmlns:app` — outra causa garantida de erro.
- **`EqualizerController.kt`** tinha várias trocas de tipo `Int`/`Short`
  que o Kotlin não converte sozinho (diferente do Java) — em
  `minLevel`, `maxLevel`, `getBandLevel` e dentro de `reset()`.
- Parâmetros do limitador de áudio (`DynamicsProcessing.Limiter`) estavam
  na ordem errada — não trava a build, mas causaria travamento ao ligar
  o limitador.
- **`ImageEditorView.kt`** usava `clipOutRect()`, que só existe a partir
  do Android 8, mas o app aceita a partir do Android 6 — troquei pela
  versão compatível (`clipRect` com `Region.Op.DIFFERENCE`).
- **`ImageEditorActivity.kt`** tinha um parâmetro chamado `max` que
  colidia com a propriedade `max` do próprio `SeekBar`, fazendo os
  sliders de brilho/contraste/saturação lerem o valor errado.

## Erros corrigidos depois (19/08, compilação Kotlin de verdade)

Com o Gradle certo, o compilador Kotlin finalmente rodou e apontou 2 erros
reais e precisos:

- **`AudioPlayerActivity.kt`** — dentro de `MediaPlayer().apply { ...
  setOnCompletionListener { isPlaying = false } }`, o `isPlaying` estava
  sendo confundido com a propriedade `isPlaying` do próprio `MediaPlayer`
  (que é somente-leitura, daí o erro "Val cannot be reassigned") em vez da
  variável da tela. Corrigido qualificando explicitamente
  `this@AudioPlayerActivity.isPlaying`. É o mesmo tipo de problema do bug
  do `max` que eu já tinha corrigido no editor de fotos — nomes iguais
  dentro de blocos `apply` aninhados.
- **`EqualizerController.kt`** — o construtor `DynamicsProcessing.Limiter`
  pede **8 parâmetros**, e eu só estava passando 7 (faltava o último,
  `postGain`). Adicionei `0f` no final das duas chamadas.

## Erro corrigido depois (19/08, versão do Gradle)

Você mandou um novo log e a causa real apareceu numa linha discreta no fim:
`docs.gradle.org/9.3.0` — seu Android Studio estava rodando o projeto com o
**Gradle 9.3.0**, uma versão muito mais nova do que o AGP 8.2.2 (que este
projeto usa) sabe conversar. É isso que causava o erro estranho de "não
pode mudar a dependência depois de resolvida" se espalhando por várias
tarefas — sintoma clássico de descompasso de versão, não um bug no código.

Isso é exatamente a consequência de não ter o Gradle Wrapper (que eu já
tinha avisado que faltava): sem ele, o Android Studio escolhe sozinho
qual Gradle usar, e pegou uma versão nova demais.

Já adicionei o arquivo `gradle/wrapper/gradle-wrapper.properties`, fixando
a versão em **Gradle 8.4** (compatível e testada com o AGP 8.2.2). Só que
esse arquivo sozinho não é suficiente — falta o `gradle-wrapper.jar`
(arquivo binário) e os scripts `gradlew`/`gradlew.bat`, que eu não
consigo gerar sem internet. **Você precisa gerar isso uma vez**, é rápido:

1. No Android Studio, abra o **Terminal** (aba embaixo, ou View → Tool
   Windows → Terminal).
2. Rode este comando dentro da pasta do projeto:
   ```
   gradle wrapper --gradle-version 8.4
   ```
3. Isso usa o Gradle 9.3.0 que você já tem instalado só pra *criar* os
   arquivos do wrapper (jar + scripts) apontando pra versão 8.4 — depois
   disso o projeto passa a usar sempre 8.4, sem depender do que estiver
   instalado na sua máquina.
4. Feche e reabra o projeto no Android Studio (ou **File → Sync Project
   with Gradle Files**) e tente compilar de novo.
