"""Gera os toques próprios do Tilim em app/src/main/res/raw. Rodar: python3 tools/gerar_sons.py
Frequências entre 500 e 1500 Hz de propósito: perda auditiva da idade come primeiro os agudos."""
import math, struct, wave, pathlib

TAXA = 22050
SAIDA = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/raw"

def salvar(nome, amostras):
    with wave.open(str(SAIDA / f"{nome}.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(TAXA)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, a)) * 30000)) for a in amostras))

def quadrada_suave(fase):  # quadrada com cantos arredondados: alta como sirene, sem chiar
    return math.tanh(4 * math.sin(fase))

def sirene(seg=2.4):  # sobe e desce 600 -> 1300 -> 600 Hz
    fase, out = 0.0, []
    for i in range(int(TAXA * seg)):
        t = i / TAXA
        f = 600 + 700 * (0.5 - 0.5 * math.cos(2 * math.pi * t / seg))
        fase += 2 * math.pi * f / TAXA
        out.append(0.9 * quadrada_suave(fase))
    return out

def bipe():  # despertador digital: bi-bi-bi-bi ... pausa
    out = []
    for _ in range(4):
        n = int(TAXA * 0.12)
        out += [0.9 * quadrada_suave(2 * math.pi * 1000 * i / TAXA) * min(1, i / 60, (n - i) / 60) for i in range(n)]
        out += [0.0] * int(TAXA * 0.08)
    return out + [0.0] * int(TAXA * 0.5)

def campainha():  # din-don de porta: dois sinos com harmônicos que decaem
    def sino(f, seg):
        return [sum(a * math.sin(2 * math.pi * f * h * i / TAXA) for h, a in ((1, .6), (2.0, .25), (3.0, .12)))
                * math.exp(-3 * i / TAXA) for i in range(int(TAXA * seg))]
    return sino(880, 0.6) + sino(698, 1.4)

def galope():  # sirene rápida (estilo ambulância "yelp")
    fase, out = 0.0, []
    for i in range(int(TAXA * 1.2)):
        t = (i / TAXA) % 0.3 / 0.3
        f = 700 + 600 * t
        fase += 2 * math.pi * f / TAXA
        out.append(0.9 * quadrada_suave(fase))
    return out

if __name__ == "__main__":
    for nome, f in (("sirene", sirene), ("sirene_rapida", galope), ("bipe", bipe), ("campainha", campainha)):
        a = f()
        assert a and max(abs(x) for x in a) <= 1, nome
        salvar(nome, a)
        print(nome, f"{len(a) / TAXA:.1f}s")
