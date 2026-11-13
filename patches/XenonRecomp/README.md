# Parches a XenonRecomp para Child of Light

Fuente: `wivi514/Dead_Rising_2_Case_Zero_Xenon_Recomp`, carpeta
`patches/XenonRecomp/` (14 parches, MIT — mismo upstream, misma licencia).
Se conservan los nombres y la numeración originales para poder comparar
con su repo. Se aplican con `tools/build-xenonrecomp.sh` sobre un
XenonRecomp limpio (HEAD de hedge-dev, sin commits desde 2025-08-04).

## Incluidos (Fase 1)

| # | Parche | Por qué para CoL |
|---|---|---|
| 0001 | Switch dispatch en los 32 bits bajos | Correctitud de switches |
| 0004 | 32 mnemónicos (Asura's Wrath) | Cubre ~5.219 de las 9.389 instrucciones no reconocidas de CoL (bdzf, stfsu, sthu, lfsu, lhzu, vsel128, eqv, addc, addme, subfze, vsubshs, vaddsws, vpkswss128, vcfpuxws128, ...) |
| 0005 | Ramas bd* a destinos fuera de función → tail calls + fix cast `__int128` | Las 67 ramas caídas restantes incluyen destinos bd*; el fix de cast lo necesita 0004 (mulhd/mulhdu) |
| 0006 | Borrar `ppc_recomp.N.cpp` viejos al regenerar | Higiene del out dir |
| 0008 | Switches con `values` + quitar `__builtin_unreachable` del default | Nuestro TOML trae `values` en 94 de 192 tablas; sin esto, solo el caso 0 coincidiría y el `unreachable` dejaría que clang quitara el bounds check |
| 0011 | 7 mnemónicos vector load/store (Case Zero) | Cubre vspltish (609), lhbrx (86), stfsux (14), vpkuwus128 (6) |
| 0012 | sync/lwsync/eieio → barreras reales | Correctitud multihilo (el stock no emite nada, ni siquiera una barrera de compilador) |
| 0015 | vaddsbs, vsububm, vpkswus/vpkswus128, vpkshss/vpkshss128 (CoL, lote C) | 52 sitios; SIMDe `adds_epi8`, `sub_epi8`, `packus_epi32`, `packs_epi16` (run 7: 4.160 → 4.108) |
| 0016 | vavguh, vcmpequh, vcmpgtsh, vcmpgtsw, vmaxsh, vminsh, vminsw (CoL, lote B) | 552 sitios; dotted forms con CR6 (run 8: 4.108 → 3.556). El borrador traía 4 casos duplicados ya existentes (VCMPEQUW, VCMPGTUH, VMAXSW, VMRGHB con cuerpo de VMINFP); eliminados por el coordinador |
| 0017 | cror, crorc, ldbrx, ldux, lfdux, stdux (CoL, lote D) | 76 sitios; update-form indexados + CR bit OR (run 9: 3.556 → 3.480) |
| 0018 | vslh, vsrah, vsrh, vsrab, vrlh (CoL, lote A) | 3.480 sitios; bucles por elemento según plantillas VSLB/VSLW/VSRAW (run 10: 3.480 → **0**) |
| 0019 | vcmpgtuh. → CR6 (fix de caso existente, no mnemónico nuevo) | El caso VCMPGTUH (0004) no manejaba la forma con punto: 36 avisos RC-bit; añadido `setFromMask` 0xFFFF (run 11: avisos 36 → 0) |
| 0020 | `__imp__` imports → `PPC_FUNC_IMPL` (extern "C") en ppc_recomp_shared.h | El runtime define los imports con C linkage (REX_EXPORT); el header los declaraba C++ y el link fallaba en los 40 imports referenciados (Fase 2, build 7). Aplicado a mano a los dos ppc_recomp_shared.h (recomp/ppc/ + port copy); `git apply --check` OK contra el checkout |

## NO incluidos (de momento)

| # | Parche | Motivo |
|---|---|---|
| 0002 | VUPKD3D128 float16 | CoL no usa VUPKD3D128 (no aparece en no reconocidas) |
| 0003 | VNOR/VNOR128 | CoL no usa VNOR/VOR |
| 0007 | Guard `PPC_FUNC_PROLOGUE` en ppc_context.h | Solo importa si el runtime incluye el header dos veces; se revisará en la fase Android |
| 0009 | VPKD3D128 float16_2 pack | CoL no usa VPKD3D128 |
| 0010 | Clave AES devkit al fallar la retail | Nuestro XEX es retail (Encryption: Normal) |
| 0013 | Elegir MSVC runtime en CMake | Solo Windows/MSVC |
| 0014 | Fences ARM64 para lwsync/eieio | Fase Android (runtime ARM64); se añadirá entonces |

## Parches propios de CoL (completos, runs 7–11)

Los 26 mnemónicos que faltaban tras 0004+0011 (4.160 sitios) están
todos implementados en los parches 0015–0018, cada uno con su prueba
(conteo antes/después en el log de XenonRecomp, ver
`analysis/FASE1.md`): **9.389 → 0 instrucciones no reconocidas**,
0 jump-outside, 0 ramas caídas, 0 avisos RC-bit (0019). No queda
ningún lowering pendiente para este juego.
