import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HubTabsComponent, HubTab } from '../hub-tabs/hub-tabs.component';

@Component({
  selector: 'app-communication-hub',
  standalone: true,
  imports: [RouterOutlet, HubTabsComponent],
  template: `
    <app-hub-tabs [tabs]="tabs" />
    <router-outlet />
  `
})
export class CommunicationHubComponent {
  tabs: HubTab[] = [
    { path: '/communication/announcements', label: 'Announcements', icon: '📢' },
    { path: '/communication/events', label: 'Events', icon: '📅' },
    { path: '/communication/messages', label: 'Messages', icon: '💬' },
  ];
}
