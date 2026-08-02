import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HubTabsComponent, HubTab } from '../hub-tabs/hub-tabs.component';

@Component({
  selector: 'app-news-hub',
  standalone: true,
  imports: [RouterOutlet, HubTabsComponent],
  template: `
    <app-hub-tabs [tabs]="tabs" />
    <router-outlet />
  `
})
export class NewsHubComponent {
  tabs: HubTab[] = [
    { path: '/news/announcements', label: 'Announcements', icon: '📢' },
    { path: '/news/events', label: 'Events', icon: '📅' },
  ];
}
