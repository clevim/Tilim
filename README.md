# Tilim 🔔

**Agenda sonora para Android, feita para pessoas idosas.**

O Tilim lê os compromissos do Google Agenda que já está no celular e, na hora certa, toca um **alarme alto em tela cheia**, com vibração e voz lendo o compromisso. Não tem conta, servidor nem IA: tudo fica no aparelho.

## Por que o Tilim existe

O Google Agenda funciona bem para quem já está acostumado com celular, mas para muita gente mais velha ele atrapalha:

- **O lembrete passa batido.** O aviso padrão é uma notificação pequena e um som curto, que some sozinha. Quem escuta pouco, está em outro cômodo ou deixa o celular no silencioso nem percebe. O próprio Google Agenda não tem opção de alarme em tela cheia. Nos celulares Samsung, o app **Calendário da Samsung** tem o estilo de alerta "Forte" (tela cheia com som longo), mas a opção fica escondida nos ajustes e às vezes depende de liberar "Alertas em tela cheia" em Acesso especial. Na maioria dos outros aparelhos (Motorola, Xiaomi, Pixel…) não existe nada parecido.
- **A tela tem informação demais.** Visão de mês, cores, ícones, menus escondidos, letra pequena. Achar "o que eu tenho hoje" exige vários toques.
- **Configurar lembrete é difícil.** Para cada compromisso, a pessoa precisa lembrar de abrir as opções e escolher o aviso. Na prática ninguém faz isso, e o compromisso fica sem aviso.
- **Criar um compromisso exige muitos passos**, cheios de campos que não importam (fuso horário, convidados, videochamada…).

O Tilim **não substitui** o Google Agenda: ele usa a mesma agenda e muda só a forma de mostrar e de avisar.

| No Google Agenda | No Tilim |
|---|---|
| Notificação pequena que some (tela cheia só no Calendário da Samsung, e escondida nos ajustes) | **Alarme de despertador em tela cheia**, alto, vibrando e com **voz falando** o compromisso, o horário e o local, até a pessoa apertar o botão |
| Cada evento precisa de lembrete configurado | **Todo compromisso já chega com aviso** (1 h e 30 min antes), sem fazer nada |
| Calendário do mês, cheio de cores e ícones | Uma **folhinha de parede**: o dia de hoje em número grande e a lista simples do que tem |
| Letra pequena | Letra grande e legível (Lexend), com o tamanho do sistema respeitado até um limite para não quebrar a tela |
| Muitos campos para criar evento | O essencial: o quê, que dia, que horas, se repete |
| Toques agudos e baixinhos | 12 toques **graves e médios** (500–1500 Hz), que quem tem perda auditiva da idade escuta melhor |

**Uso em família:** um filho ou neto pode marcar a consulta pelo Google Agenda no próprio celular, numa agenda compartilhada. O compromisso aparece sozinho no Tilim do pai ou da avó e o alarme toca na hora, sem a pessoa idosa precisar mexer em nada.

## O que ele faz

- **Lê o Google Agenda do aparelho** (via `CalendarContract`): o que a família marca na agenda compartilhada aparece no Tilim.
- **Avisos já configurados**: todo compromisso chega com os avisos padrão (inicialmente **1 hora antes e 30 minutos antes**), sem precisar configurar nada. Na tela de Ajustes dá para trocar o padrão, e cada compromisso pode ter os seus.
  - Opções: véspera às 20h, 1 h, 30 min, 10 min. Compromissos de dia inteiro avisam "No dia, às 8h".
- **Alarme em tela cheia**: tela terracota com sino balançando, som alto, vibração e **voz (TTS)** falando o nome do compromisso.
- **12 toques** para escolher, todos na faixa de 500 a 1500 Hz (a perda auditiva da idade afeta primeiro os agudos).
- **Criar, editar e apagar** compromissos, com repetição.
- **Widget** na tela inicial mostrando o próximo compromisso.
- Visual de **folhinha de parede**: número do dia grande, fonte Lexend, sem cartões e sem enfeite.
- Os avisos continuam funcionando **depois de reiniciar** o celular.
- **Se atualiza sozinho pelo GitHub**: quando sai versão nova, aparece no topo da tela "Chegou uma versão nova do Tilim" com o botão **Atualizar agora**. Ele baixa e abre o instalador. Na primeira vez, o Android pede para deixar o Tilim instalar atualizações; o próprio app explica o passo.

## Requisitos

| | |
|---|---|
| Android | 8.0 (API 26) ou mais novo |
| Para compilar | JDK 17 e Android SDK (API 36) |

## Compilar localmente

```bash
git clone git@github.com:clevim/Tilim.git
cd Tilim

# o Gradle precisa saber onde está o SDK: crie local.properties
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew testDebugUnitTest   # testes
./gradlew lintRelease         # lint (correção, segurança, desempenho)
./gradlew assembleDebug       # APK de debug
./gradlew assembleRelease     # APK de release (minificado)
```

Os APKs ficam em `app/build/outputs/apk/<debug|release>/`.

Para instalar num celular ligado no USB (com depuração USB ativa):

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

> Sem as variáveis de assinatura (ver abaixo), o `assembleRelease` assina com a **chave de debug**. Serve para testar, mas não para distribuir.

## Estrutura

```
app/src/main/java/com/clevs/tilim/
  MainActivity.kt   telas (Compose): lista do dia, novo/editar compromisso, ajustes
  Agenda.kt         leitura e escrita no Google Agenda (CalendarContract)
  Avisos.kt         cálculo e agendamento dos alarmes
  Toque.kt          alarme em tela cheia: som, vibração e voz
  Widget.kt         widget do próximo compromisso
app/src/main/res/raw/   toques
tools/
  gerar_sons.py     gera os toques próprios (sirene, bipe, campainha)
  baixar_sons.sh    baixa e padroniza os toques de terceiros (precisa de ffmpeg)
```

Licenças dos sons de terceiros: `app/src/main/assets/licencas_sons.txt`.

## Deploy (GitHub Actions)

O workflow `.github/workflows/android.yml` faz:

| Evento | O que acontece |
|---|---|
| Push na `main` ou Pull Request | roda testes e lint e gera o APK (baixável em *Actions → execução → Artifacts*) |
| Push de uma tag `v*` (ex.: `v1.5.0`) | confere se a tag bate com o `versionName`, roda testes e lint, gera o APK **assinado com a chave de release** e publica uma **Release** no GitHub com o APK anexado |

### 1. Criar a chave de release (uma vez só)

```bash
keytool -genkeypair -v -keystore tilim-release.jks -alias tilim \
  -keyalg RSA -keysize 4096 -validity 10000
```

> ⚠️ **Guarde esse arquivo e as senhas em lugar seguro** (gerenciador de senhas + backup). Se perder a chave, não dá mais para publicar atualizações: os celulares vão recusar o APK novo e será preciso desinstalar o app.
> O `.gitignore` já impede commitar `*.jks` e `*.keystore`.

### 2. Cadastrar os secrets no GitHub

Em **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | o arquivo `.jks` em base64: `base64 -w0 tilim-release.jks` |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | `tilim` (o alias usado no `keytool`) |
| `KEY_PASSWORD` | senha da chave |

Ou pelo terminal, com o [GitHub CLI](https://cli.github.com/):

```bash
base64 -w0 tilim-release.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
```

### 3. Publicar uma versão

1. Em `app/build.gradle.kts`, aumente o `versionCode` (sempre +1; o Android recusa atualizar para um número igual ou menor) e ajuste o `versionName`. **A tag precisa ser igual ao `versionName` com um `v` na frente** (`versionName = "1.5.0"` → tag `v1.5.0`). O app compara as duas para saber se há atualização, e o CI recusa a tag se forem diferentes.
2. Commit e push na `main`.
3. Crie e envie a tag:

   ```bash
   git tag v1.5.0
   git push origin v1.5.0
   ```

4. Acompanhe em **Actions**. Quando terminar, o APK `Tilim-v1.5.0.apk` estará em **Releases**.

### Assinar com a chave de release localmente (opcional)

O build lê as mesmas variáveis que o CI usa:

```bash
export TILIM_KEYSTORE=/caminho/para/tilim-release.jks
export TILIM_KEYSTORE_PASSWORD=...
export TILIM_KEY_ALIAS=tilim
export TILIM_KEY_PASSWORD=...
./gradlew assembleRelease
```

### Trocando da chave de debug para a de release

As versões antigas foram assinadas com a chave de debug. O Android não deixa atualizar um app assinado com outra chave, então **na primeira instalação da versão de release é preciso desinstalar o Tilim antigo** antes. Os compromissos não se perdem, porque ficam no Google Agenda. Só os ajustes do app (avisos padrão, toque escolhido) voltam ao padrão.

## Instalar no celular de quem vai usar

1. No celular, abra a página de **Releases** do repositório e baixe o `.apk` mais recente.
2. Ao abrir, o Android vai pedir para **permitir instalar apps desta fonte**. Permita para o navegador ou o gerenciador de arquivos.
3. Abra o Tilim e **aceite todas as permissões**: agenda, notificações, alarmes e tela cheia.
4. Quando ele pedir, **tire o Tilim da otimização de bateria**. Sem isso, alguns fabricantes (Xiaomi, Samsung, Motorola…) atrasam ou bloqueiam os alarmes.
5. Confira se a conta Google com a agenda certa está sincronizada no celular.

## Permissões usadas

| Permissão | Para quê |
|---|---|
| `READ_CALENDAR` / `WRITE_CALENDAR` | ler e criar/editar compromissos |
| `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` | mostrar o alarme em tela cheia |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | tocar no minuto exato |
| `RECEIVE_BOOT_COMPLETED` | reagendar os avisos depois de reiniciar |
| `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE*` | tocar e vibrar com a tela desligada |
| `ACCESS_NOTIFICATION_POLICY` | tocar mesmo no modo "Não perturbe" |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | evitar que o sistema mate os alarmes |
| `INTERNET`, `REQUEST_INSTALL_PACKAGES` | procurar versão nova no GitHub e instalar a atualização |
