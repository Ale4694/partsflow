import { Routes } from '@angular/router';

// Every screen is loaded on demand (lazy), so the first page load stays small.
export const routes: Routes = [
  {
    path: 'fornitori',
    title: 'Fornitori',
    loadComponent: () => import('./suppliers/supplier-list').then((m) => m.SupplierList),
  },
  {
    path: 'fornitori/:id',
    title: 'Fornitore',
    loadComponent: () => import('./suppliers/supplier-detail').then((m) => m.SupplierDetail),
  },
  {
    path: 'articoli',
    title: 'Articoli',
    loadComponent: () => import('./items/item-list').then((m) => m.ItemList),
  },
  { path: '**', redirectTo: '' },
];
