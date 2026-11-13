# Fase 1 — Helpers CRT, switch-tails, ramas caídas y lowerings (COMPLETA)

Fecha: 2026-11-13 (noche, trabajo autónomo). Base: `analysis/FASE0.md`
(76.565 funciones, 192 jump tables, 9.389 instrucciones no reconocidas).

## 1. Helpers CRT: las 8 direcciones descubiertas (HECHO)

Con `find_save_restore.py` de wivi514 sobre la imagen plana:

| Helper | Dirección |
|---|---|
| savegprlr_14 | 0x82A364C0 |
| restgprlr_14 | 0x82A36510 |
| savefpr_14 | 0x82A376F0 |
| restfpr_14 | 0x82A3773C |
| savevmx_14 | 0x82A39540 |
| savevmx_64 | 0x82A395D4 |
| restvmx_14 | 0x82A397D8 |
| restvmx_64 | 0x82A3986C |

Verificadas contra las fórmulas de tamaño de `Recompiler::Analyse()`
(cola `stw r12,-8(r1); blr` en save, `lwz r12,-8(r1); mtlr; blr` en
restore). Puestas en `recomp/col.toml`.

**Efecto medido (run 2 vs run 1):** ramas caídas en el C++ generado
**23.998 → 513 líneas** (−98 %). Las 8 líneas `ERROR: __restgprlr_14
address is unspecified` desaparecieron.

## 2. Switch-tails: 369 → 0 (HECHO)

`tools/fix_switch_function_bounds.py` (adaptado de wivi514 a las rutas y
al rango .text de CoL, 0x82170000..0x82CD2210) calculó 25 overrides de
tamaño, que se aplicaron en `recomp/col.toml` (`functions = [...]`).

**Efecto medido (run 3 vs run 2):** `jump outside function`
**369 → 0 líneas**; `// ERROR: 0x` en el C++ generado **369 → 0**.
Ramas caídas (forma sin dos puntos) **144 → 67** de paso, porque el
ensanchado absorbió varios destinos.

## 3. Ramas caídas: 69 → 0 (HECHO)

`find_dropped_branches.py --widen` (wivi514) ensanchó 31 funciones
truncadas (forward). **Efecto medido (run 5 vs run 4): 69 → 5 líneas**;
la clase BACKWARD quedó en 0. Segunda ronda de `--widen` (5 funciones
más): **run 6: 5 → 0 líneas — cero ramas caídas** (la herramienta lo
confirma; el mismo chequeo antes reportaba 69 y 5, así que el 0 es real).

## 4. Lowerings: 9.389 → 4.160 con parches de wivi514 (HECHO, primera mitad)

Parches en `patches/XenonRecomp/` (nombres originales de wivi514, ver
`patches/XenonRecomp/README.md`) aplicados con
`tools/build-xenonrecomp.sh` sobre un XenonRecomp limpio:

| Parche | Contenido |
|---|---|
| 0001 | Switch dispatch en los 32 bits bajos |
| 0004 | 32 mnemónicos (Asura's Wrath) |
| 0005 | Ramas bd* a destinos fuera de función → tail calls + fix cast `__int128` |
| 0006 | Borrar `ppc_recomp.N.cpp` viejos al regenerar |
| 0008 | Switches con `values` + quitar `__builtin_unreachable` del default (nuestro TOML trae `values` en 94 de 192 tablas) |
| 0011 | 7 mnemónicos vector load/store (Case Zero) |
| 0012 | sync/lwsync/eieio → barreras reales |

**Efecto medido (run 4 vs run 3): no reconocidas 9.389 → 4.160**
(−56 %). Jump-outside sigue en 0. Los 14 parches de wivi514 aplican
limpios en secuencia sobre el HEAD actual de XenonRecomp (verificado
con `git apply --check` en orden); los no incluidos (0002, 0003, 0007,
0009, 0010, 0013, 0014) no los usa CoL o son de fase Android (ver
README de parches).

## 5. Lowerings propios de CoL: 4.160 restantes (EN CURSO)

Restan 26 mnemónicos (run 4):

| Mnemonics | Sitios |
|---|---|
| vslh 2.420, vsrah 1.024 | 3.444 |
| vmaxsh 265, vminsh 92, vminsw 1 | 358 |
| vcmpgtsh 58+24, vcmpgtsw 24+48, vcmpequh 16+8 | 178 |
| stdux 30, ldux 26, ldbrx 15, lfdux 1 | 72 |
| vaddsbs 26, vsububm 7, vpkswus128 12, vpkshss 6+1 | 52 |
| vrlh 18, vavguh 16, vsrh 12, vsrab 6 | 52 |
| cror 2, crorc 2 | 4 |

Diseño repartido en 4 lotes paralelos (borradores en
`patches/drafts/`): A shifts/rotates (vslh, vsrah, vsrh, vsrab, vrlh),
B compares/min/max/avg, C arith/pack, D loads/stores/CR. Aplicación y
verificación en serie por el coordinador, un lote verificado antes de
seguir. Objetivo: 4.160 → 0.

### Lote C verificado (run 7, 2026-11-13)

Parche `patches/XenonRecomp/0015-recompiler-vaddsbs-vsububm-vpkswus-vpkshss.patch`
(5 casos: vaddsbs, vsububm, vpkswus/vpkswus128, vpkshss/vpkshss128;
SIMDe `adds_epi8`, `sub_epi8`, `packus_epi32`, `packs_epi16`).
**Efecto medido (run 7 vs run 6): no reconocidas 4.160 → 4.108 (−52,
exactamente los 52 sitios del lote; 0 menciones de los 5 mnemónicos
en `analysis/xenonrecomp-run7.log`)**; jump-outside 0, ramas caídas 0,
funciones 76.565.

### Lote B verificado (run 8, 2026-11-13)

Parche `patches/XenonRecomp/0016-recompiler-vavguh-vcmpequh-vcmpgtsh-vcmpgtsw-vmaxsh-vminsh-vminsw.patch`
(7 casos: vavguh, vcmpequh, vcmpgtsh, vcmpgtsw, vmaxsh, vminsh, vminsw).
**Efecto medido (run 8 vs run 7): no reconocidas 4.108 → 3.556 (−552,
exactamente los 552 sitios del lote; 0 menciones de los 7 mnemónicos
en `analysis/xenonrecomp-run8.log`)**; jump-outside 0, ramas caídas 0.
Reparación del coordinador: el borrador del diseñador traía 4 casos
espurios que YA existían en el árbol (vía wivi514 0004) — VCMPEQUW,
VCMPGTUH, VMAXSW y un VMRGHB con cuerpo copiado de VMINFP — que
habrían sido errores de `duplicate case` al compilar; se eliminaron
y solo los 7 casos realmente ausentes entraron en el parche.

### Lote D verificado (run 9, 2026-11-13)

Parche `patches/XenonRecomp/0017-recompiler-cror-crorc-ldbrx-ldux-lfdux-stdux.patch`
(6 casos: cror, crorc, ldbrx, ldux, lfdux, stdux).
**Efecto medido (run 9 vs run 8): no reconocidas 3.556 → 3.480 (−76,
exactamente los 76 sitios del lote; 0 menciones de los 6 mnemónicos
en `analysis/xenonrecomp-run9.log`)**; jump-outside 0, ramas caídas 0.

Restan 3.480 sitios = exactamente el lote A (vslh 2.420 + vsrah 1.024 +
vrlh 18 + vsrh 12 + vsrab 6).

### Lote A verificado (run 10, 2026-11-13) — OBJETIVO CUMPLIDO

Parche `patches/XenonRecomp/0018-recompiler-vslh-vsrah-vsrh-vsrab-vrlh.patch`
(5 casos: vslh, vsrah, vsrh, vsrab, vrlh; bucles por elemento
siguiendo las plantillas VSLB/VSLW/VSRAW).
**Efecto medido (run 10 vs run 9): no reconocidas 3.480 → 0.
CERO instrucciones no reconocidas; 0 líneas `error` en el log;
jump-outside 0, ramas caídas 0, exit 0, 300 .cpp (209 MB).**
Los 5 mnemónicos verificados ausentes antes del parche (sin
duplicados) y con sus IDs Capstone reales; el borrador del diseñador
vino limpio, sin reparación.

### Parche 0019: vcmpgtuh. sin CR6 (run 11, 2026-11-13)

Al llegar a 0 no reconocidas quedaba UNA clase de aviso en el log:
36 líneas `vcmpgtuh. … has RC bit enabled but no comparison was
generated` (idénticas en run4 y run10). El caso VCMPGTUH existente
(vía wivi514 0004) no manejaba la forma con punto: 36 sitios del
juego ponen RC sin actualizar CR6. Parche
`patches/XenonRecomp/0019-recompiler-vcmpgtuh-dotted-cr6.patch`
(añade el `setFromMask` 0xFFFF igual que VCMPEQUB/VCMPGTSH; no es un
mnemónico nuevo, es arreglar un caso existente).
**Efecto medido (run 11): avisos RC-bit 36 → 0; no reconocidas
siguen en 0; 0 líneas `error`; jump-outside 0, ramas caídas 0.**
El código generado ya emite `setFromMask` en esos sitios
(verificado en `recomp/ppc/ppc_recomp.219–221.cpp`).

## Estado final Fase 1

| Métrica | Fase 0 (run 1) | Final (run 11) |
|---|---|---|
| Funciones | 76.565 | 76.565 |
| Jump-outside (switch-tail) | 369 | **0** |
| Ramas caídas | 23.998 | **0** |
| No reconocidas | 9.389 | **0** |
| Avisos RC-bit | 36 | **0** |
| Líneas `error` en el log | — | **0** |

El recompilado completo del juego genera 300 .cpp (209 MB) sin
errores ni avisos. **Fase 1 cerrada: el XEX entero se recompila.**

Caveats que quedan documentados (no bloquean la recompilación):
- Orden de lanes de los packs (0015): heredado de los casos pack
  existentes sin re-derivarlo del layout del vector; merece una
  prueba de vector real antes de dar los packs por probados.
- El C++ generado NO se ha compilado aún: necesita `ppc_recomp_shared.h`
  del runtime ReXGlue (no está en esta VM); la compilación real es el
  workflow rexauto en CI (siguiente fase).

## Pendiente (tras Fase 1)

- Compilar el C++ generado con el runtime ReXGlue (workflow rexauto
  en CI) y corregir lo que salga.
- Renderer nativo / perfil Mali-G57, montaje de DLCs, control
  Igniculus, reconciliación ReXGlue v0.10.0 vs v0.10.1.

Caveat documentado en el parche 0015: el orden de lanes de los packs
se heredó de los casos existentes (VPKSHUS/VPKSWSS/VPKUHUS/VPKUWUM/
VPKUWUS) sin re-derivarlo del layout de almacenamiento del vector;
merece una prueba de vector real antes de dar los packs por probados.

## Historial de runs (todos exit 0)

| Run | Cambio | Funciones | Jump-outside | No reconocidas | Ramas caídas |
|---|---|---|---|---|---|
| 1 (Fase 0) | trial, helpers en 0 | 76.565 | 369 | 9.389 | 23.998 |
| 2 | helpers CRT reales | 76.565 | 369 | 9.389 | 513 |
| 3 | + switch bounds (25) | 76.565 | 0 | 9.389 | 67 |
| 4 | + 7 parches wivi514 | 76.565 | 0 | 4.160 | 69 |
| 5 | + widen 31 funciones | 76.565 | 0 | 4.160 | 5 |
| 6 | + widen ronda 2 (5 más) | 76.565 | 0 | 4.160 | **0** |
| 7 | + parche 0015 (lote C: 5 mnemónicos) | 76.565 | 0 | **4.108** | 0 |
| 8 | + parche 0016 (lote B: 7 mnemónicos) | 76.565 | 0 | **3.556** | 0 |
| 9 | + parche 0017 (lote D: 6 mnemónicos) | 76.565 | 0 | **3.480** | 0 |
| 10 | + parche 0018 (lote A: 5 mnemónicos) | 76.565 | 0 | **0** | 0 |
| 11 | + parche 0019 (vcmpgtuh. → CR6) | 76.565 | 0 | **0** | 0 |
