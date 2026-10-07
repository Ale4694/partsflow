import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ConfirmDialog } from '../confirm';
import { Notifications } from '../notification';

interface NavLink {
  path: string;
  label: string;
}

/** The frame around every screen: sidebar on the left, the current screen on the right. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Notifications, ConfirmDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  protected readonly links: NavLink[] = [
    { path: '/', label: 'Dashboard' },
    { path: '/fornitori', label: 'Fornitori' },
    { path: '/articoli', label: 'Articoli' },
    { path: '/magazzino', label: 'Magazzino' },
    { path: '/importazioni', label: 'Importazioni' },
    { path: '/assistente', label: 'Assistente' },
  ];
}
