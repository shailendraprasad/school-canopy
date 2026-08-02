import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HubTabsComponent, HubTab } from '../hub-tabs/hub-tabs.component';

@Component({
  selector: 'app-people-hub',
  standalone: true,
  imports: [RouterOutlet, HubTabsComponent],
  template: `
    <app-hub-tabs [tabs]="tabs" />
    <router-outlet />
  `
})
export class PeopleHubComponent {
  tabs: HubTab[] = [
    { path: '/people/teachers', label: 'Teachers', icon: '👩‍🏫' },
    { path: '/people/staff', label: 'Office Staff', icon: '🏢' },
  ];
}
