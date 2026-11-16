import subprocess, re, numpy as np, wave, sys
SRC="/home/claude/tarbound/dev/sounds_original"
DST="/home/claude/tarbound/src/main/resources/assets/tarbound/sounds/radio"
TARGET={"static1":-24.0,"static2":-24.0,"tune":-22.0}
SR=44100; WIN=220            # 5 ms envelope windows
MAX_RISE_DB_PER_10MS=6.0
PEAK=10**(-6/20)

def read(path, af=None):
    cmd=["ffmpeg","-v","error","-i",path]+(["-af",af] if af else [])+["-f","f32le","-ac","1","-ar",str(SR),"-"]
    return np.frombuffer(subprocess.run(cmd,capture_output=True,check=True).stdout,dtype=np.float32).copy()

def lufs_of(a):
    tmp="tmp_measure.wav"; write_wav(tmp,a)
    out=subprocess.run(["ffmpeg","-hide_banner","-nostats","-i",tmp,"-af","ebur128","-f","null","-"],capture_output=True,text=True).stderr
    return float(re.findall(r"I:\s+(-?[\d.]+) LUFS",out)[-1])

def write_wav(path,a):
    pcm=(np.clip(a,-1,1)*32767).astype(np.int16)
    with wave.open(path,"wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())

def rise_limit(a):
    n=len(a)//WIN
    env=np.sqrt((a[:n*WIN].reshape(n,WIN)**2).mean(1))+1e-9
    step=10**(MAX_RISE_DB_PER_10MS/20*(WIN/441))     # allowed growth per 5 ms window
    floor=10**(-50/20)
    allowed=np.empty_like(env); allowed[0]=min(env[0],floor)
    for i in range(1,n):
        allowed[i]=min(env[i], max(allowed[i-1],floor)*step)
    g=np.minimum(1.0, allowed/env)
    # smooth gain to sample rate (linear interp between window centres)
    centres=(np.arange(n)+0.5)*WIN
    gs=np.interp(np.arange(len(a)),centres,g)
    return a*gs

for name,target in TARGET.items():
    src=f"{SRC}/{name}.ogg"
    a=read(src,"highpass=f=110:poles=2,lowpass=f=6500:poles=2,lowpass=f=6500:poles=2,"
                "acompressor=threshold=-28dB:ratio=5:attack=1:release=90:knee=6:makeup=1")
    a=rise_limit(a)
    # fades
    fi=int((0.04 if name=="tune" else 0.02)*SR); fo=int(0.06*SR)
    a[:fi]*=np.linspace(0,1,fi); a[-fo:]*=np.linspace(1,0,fo)
    # loudness to target, then hard ceiling
    a*=10**((target-lufs_of(a))/20)
    peak=np.abs(a).max()
    if peak>PEAK:
        a=np.tanh(a/PEAK)*PEAK*0.999   # gentle soft-clip to the ceiling (only hits the very top)
    write_wav(f"{name}_final.wav",a)
    subprocess.run(["ffmpeg","-v","error","-y","-i",f"{name}_final.wav","-c:a","libvorbis","-q:a","5","-ac","1","-ar",str(SR),f"{DST}/{name}.ogg"],check=True)

def stats(p):
    a=read(p); w=441; n=len(a)//w
    db=20*np.log10(np.sqrt((a[:n*w].reshape(n,w)**2).mean(1)+1e-12))
    j=np.diff(db); aud=db[1:]>-40
    out=subprocess.run(["ffmpeg","-hide_banner","-nostats","-i",p,"-af","ebur128=peak=true","-f","null","-"],capture_output=True,text=True).stderr
    I=float(re.findall(r"I:\s+(-?[\d.]+) LUFS",out)[-1]); P=float(re.findall(r"Peak:\s+(-?[\d.]+) dBFS",out)[-1])
    return I,P,(j[aud].max() if aud.any() else 0),db.max()
print(f"{'file':8} {'LUFS':>14} {'peak dBFS':>16} {'onset jump dB':>18} {'loudest 10ms':>18}")
for f in TARGET:
    o=stats(f"{SRC}/{f}.ogg"); n=stats(f"{DST}/{f}.ogg")
    print(f"{f:8} {o[0]:6.1f} -> {n[0]:5.1f} {o[1]:7.1f} -> {n[1]:5.1f} {o[2]:8.1f} -> {n[2]:5.1f} {o[3]:8.1f} -> {n[3]:5.1f}")
