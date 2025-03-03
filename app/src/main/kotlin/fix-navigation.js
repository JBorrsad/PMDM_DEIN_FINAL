// Script para asegurar que la barra de navegación lateral se muestre correctamente
document.addEventListener('DOMContentLoaded', function() {
    applyNavigationFix();
    
    // Intentar de nuevo después de un retraso por si el DOM no está completamente cargado
    setTimeout(applyNavigationFix, 1000);
});

// Función principal para aplicar correcciones a la navegación
function applyNavigationFix() {
    // Asegurar que la navegación esté visible
    var navigation = document.getElementById('navigation-wrapper');
    if (navigation) {
        navigation.style.display = 'block';
        navigation.style.visibility = 'visible';
    }

    // Asegurar que la barra lateral está visible
    var sidebar = document.getElementById('leftColumn');
    if (sidebar) {
        sidebar.style.display = 'block';
        sidebar.style.visibility = 'visible';
        sidebar.style.minWidth = '250px';
    }

    // Asegurar que el menú lateral está visible
    var sideMenu = document.getElementById('sideMenu');
    if (sideMenu) {
        sideMenu.style.display = 'block';
        sideMenu.style.visibility = 'visible';
    }

    // Ajustar el contenido principal para dejar espacio a la barra lateral
    var main = document.getElementById('main');
    if (main) {
        main.style.marginLeft = '260px';
    }

    // Comprobar si hay filtros disponibles y mostrarlos
    var filterSection = document.getElementsByClassName('filter-section');
    if (filterSection.length > 0) {
        filterSection[0].style.display = 'flex';
        filterSection[0].style.visibility = 'visible';
    }
    
    // También buscar y corregir elementos específicos de la navegación
    var allTabs = document.querySelectorAll('.tabs-section');
    if (allTabs.length > 0) {
        allTabs.forEach(function(tab) {
            tab.style.display = 'block';
            tab.style.visibility = 'visible';
        });
    }

    console.log('Navigation fix script ejecutado');
}

// Ejecutar también cuando la ventana esté completamente cargada
window.onload = applyNavigationFix; 