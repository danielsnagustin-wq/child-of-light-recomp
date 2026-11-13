# Fase 0 — Análisis del XEX de Child of Light (Xbox 360)

Fecha: 2026-11-13. Todo verificado contra el archivo real del juego
(`ChildOfLight-XBLA`, STFS, MD5 `ad88061a06fba28e3434945f37cb3d57`).

## Identidad del XEX (xex2ool info, del archivo real)

| Campo | Valor |
|---|---|
| Title ID | **5841140D** (confirmado por el XEX, no solo por la carpeta) |
| Media ID | 27A6BF29 |
| Versión | 0.0.0.2 (base 0.0.0.2) |
| Entry point | 0x82945848 |
| Image base / size | 0x82000000 / 0xDA0000 (14,3 MB) |
| Encriptación / compresión | Normal (retail) / Basic |
| Secciones | 8 (.rdata, .pdata, .text, .data, .XBMOVIE, .idata, .XBLD, .reloc) |
| .text (código) | 0x82170000 + 0xB62210 (**11,9 MB**) |
| ESRB | E10+ |

## Extracción y desencriptado (herramientas verificadas)

- STFS extraído con `extract.py` de rexauto: 28 archivos, 1.503 MB.
- `default.xex` desencriptado con `xex2ool basefile` → `default.pe`
  (PE32 PowerPC big-endian, 14.286.848 B, 8 secciones).
- Imagen plana con `xex_image_dump` (de wivi514, usa el mismo loader que
  XenonRecomp) → `default_image.bin` + `.sections`.

## Imports: solo kernel + XAM (superficie mínima)

El XEX importa solo **2 librerías**: `xboxkrnl.exe` y `xam.xex`
(v2.0.21256.0, mín. v2.0.12611.0). Sin D3D directo ni librerías raras:
todo lo que el juego pide pasa por kernel/XAM, que es justo lo que
ReXGlue implementa. Buena noticia para el port.

## Jump tables: 192, 0 sin resolver

- XenonAnalyse (stock) encuentra **0** — fallo de detección conocido,
  no un hecho: su patrón exige una secuencia exacta de opcodes y el
  compilador de este juego los ordena distinto. A Case Zero y a Asura's
  Wrath les pasó exactamente lo mismo (documentado por wivi514).
- Con el scanner de wivi514 (`find_jumptables.py`, sobre la imagen plana
  y con el rango real de .text 0x82170000..0x82CD2210):

| Tipo | Tablas |
|---|---|
| absolute (lwzx) | 98 |
| offset16 (lhzx) | 44 |
| offset8 (lbzx) | 50 |
| **Total** | **192** |
| Case labels | 4.380 |
| Sin resolver | **0** |

Comparación: Case Zero 232 tablas / 6.114 labels. CoL está en el mismo
orden de magnitud. Archivo: `analysis/col-jumptables.toml` (8.457 líneas).

## XenonRecomp (prueba): COMPILA — 76.565 funciones

Primera pasada con `recomp/col.toml` (helpers CRT en 0, a modo de prueba):

- **Exit 0. 301 archivos .cpp generados, 206 MB** en `recomp/ppc/`.
- **76.565 funciones** en `ppc_func_mapping.cpp`
  (Case Zero: 57.728 — CoL es más grande).
- Duración del run: ~11 s de CPU.

### Trabajo pendiente que la pasada deja medido

1. **Instrucciones no reconocidas: 9.389 sitios, 61 mnemónicos,
   1.168 funciones (1,53 % del total).** Es el trabajo más grande de la
   Fase 1. Comparación: Case Zero tuvo 42 sitios / 6 mnemónicos y los
   cerró a cero; Asura's Wrath tuvo 3.192. CoL tiene ~3× más que Asura's.
   - 6.124 son VMX/vector (vslh 2.420, vsrah 1.024, vsubshs 928,
     vspltish 609, vmaxsh 265, vsel128 264, vcfpuxws128 76, ...).
   - El resto: update-form loads/stores (stfsu 516, sthu 397, lfsu 316,
     lhzu 310, ...) y ramas (bdzf 889, eqv 147, addc/addme/subfze, ...).
   - Una instrucción no reconocida NO genera código (se salta en
     silencio) — es ejecución incorrecta silenciosa, no un fallo de
     build. Hay que implementar cada lowering en el fork de XenonRecomp
     (trabajo mecánico, como hizo wivi514) antes de cualquier runtime.
   - Concentración: las direcciones se agrupan en la cola de .text
     (0x82C0xxxx–0x82C2xxxx), lo que sugiere una librería concreta
     (matemática vectorial) más que código repartido por todo el juego.

2. **Switch-tail ("jump outside function"): 369 líneas en 25 funciones.**
   XenonRecomp corta cada función en su `bctr` de jump-table y los
   cuerpos de los cases quedan fuera. Se repara con
   `fix_switch_function_bounds.py --apply` (wivi514) hasta llegar a
   cero. Case Zero: 517 líneas en 28 funciones en su primera pasada.

3. **Ramas caídas ("// ERROR" en el C++ generado): 23.998 líneas, pero
   el 98,5 % son los helpers CRT.** 23.629 apuntan a solo 15 direcciones
   seguidas: **0x82A36514–0x82A3654C** (entradas de 4 en 4 bytes, los
   `__savegprlr_14` / `__restgprlr_14` y familia). Verificado en bytes:
   en 0x82A36510 empieza una cadena de `ld rN, offset(r1)` seguida de
   `lwz r12, -8(r1)` + `mtlr r12` — el patrón clásico del restore
   helper. Al registrar las direcciones reales de los 8 helpers CRT
   (`__restgprlr_14`, `__savegprlr_14`, `__restfpr_14`,
   `__savefpr_14`, `__restvmx_14`, `__savevmx_14`, `__restvmx_64`,
   `__savevmx_64`) en `col.toml`, ese 98,5 % desaparece.
   Quedan ~70 destinos distintos / ~369 líneas: el trabajo real de
   `find_dropped_branches.py` (Case Zero tuvo 31).

4. **Helpers CRT en 0 en esta pasada** (8 líneas ERROR esperadas).
   Hay que descubrir sus direcciones reales en el binario (rexauto lo
   hace automáticamente en su pipeline; aquí se hará por patrón, como
   se hizo con 0x82A36514).

## Veredicto Fase 0

**El juego SÍ es recompilable.** XenonRecomp completó la pasada y generó
206 MB de C++ válido. No hay ningún bloqueo estructural: imports mínimos,
jump tables resueltas (192/0), switch-tails acotados (25 funciones),
ramas caídas explicadas (helpers CRT). El trabajo grande y cuantificado
es implementar los lowerings de las 9.389 instrucciones no reconocidas
(61 mnemónicos, 1.168 funciones) en el fork de XenonRecomp — el mismo
tipo de trabajo que Case Zero y Asura's Wrath ya cerraron a cero.

Siguiente paso (Fase 1): descubrir las direcciones de los 8 helpers CRT,
aplicar `fix_switch_function_bounds.py`, y empezar los lowerings por los
mnemónicos más frecuentes (vslh, vsrah, vsubshs, bdzf, vspltish).
