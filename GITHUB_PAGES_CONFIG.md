# Configuración de GitHub Pages para PawTracker

## 📚 Documentación disponible

La documentación funcional del proyecto PawTracker está lista para publicarse en GitHub Pages.

## 🚀 Pasos para activar GitHub Pages

### 1. Subir los cambios a GitHub

Primero, asegúrate de que todos los archivos estén en el repositorio:

```bash
git add docs/
git commit -m "Configurar GitHub Pages con documentación"
git push origin main
```

### 2. Configurar GitHub Pages en el repositorio

1. Ve a tu repositorio en GitHub: `https://github.com/[tu-usuario]/PMDM_DEIN_FINAL`
2. Haz clic en **"Settings"** (Configuración) en la barra superior
3. En el menú lateral izquierdo, busca y haz clic en **"Pages"**
4. En la sección **"Source"** (Fuente):
   - **Branch**: Selecciona `main` (o `master` si ese es el nombre de tu rama principal)
   - **Folder**: Selecciona `/docs`
5. Haz clic en **"Save"** (Guardar)

### 3. Esperar el despliegue

- GitHub Pages tardará entre 1-5 minutos en construir y publicar tu sitio
- Verás un mensaje en la parte superior indicando que tu sitio está siendo construido
- Una vez listo, aparecerá un mensaje con la URL de tu sitio

### 4. Acceder a la documentación

Tu documentación estará disponible en:

```
https://[tu-usuario].github.io/PMDM_DEIN_FINAL/
```

## 📁 Estructura de archivos

```
docs/
├── index.html              # Página principal (Memoria Final)
├── Memoria FINAL.html      # Documentación completa
├── img/                    # Carpeta de imágenes
│   ├── Ajustes.jpg
│   ├── cambiarpassword.png
│   ├── diagrama_clases.png
│   ├── EditarPerfilPerro.jpg
│   ├── EditarPerfilUsuario.jpg
│   ├── Login.jpg
│   ├── Login2.jpg
│   ├── Mapsactivity.jpg
│   ├── Mapsact_editandozonasegura.jpg
│   ├── PerfilPerro.jpg
│   ├── PerfilUsuario.jpg
│   └── SplashScreen.jpg
├── .nojekyll               # Desactiva el procesamiento de Jekyll
└── README.md               # Información sobre la documentación
```

## ⚠️ Notas importantes

- El archivo `.nojekyll` es importante para que GitHub Pages no procese los archivos con Jekyll
- Las rutas de las imágenes en el HTML son relativas (`img/...`), por lo que funcionarán correctamente
- Si necesitas actualizar la documentación, modifica los archivos en la carpeta `docs/` y haz push a GitHub

## 🔧 Solución de problemas

### La página no se carga correctamente

- Verifica que la rama seleccionada sea la correcta (main/master)
- Asegúrate de que la carpeta seleccionada sea `/docs`
- Espera unos minutos después de hacer cambios

### Las imágenes no se muestran

- Verifica que las rutas en el HTML sean relativas (no absolutas)
- Asegúrate de que todas las imágenes estén en `docs/img/`

### Error 404

- Verifica que el archivo `index.html` exista en la carpeta `docs/`
- Revisa que el repositorio sea público (o que tengas GitHub Pro para repositorios privados)

## 📝 Actualizar la documentación

Para actualizar la documentación:

1. Modifica los archivos HTML en la carpeta `docs/`
2. Haz commit y push:
   ```bash
   git add docs/
   git commit -m "Actualizar documentación"
   git push origin main
   ```
3. GitHub Pages se actualizará automáticamente en unos minutos

