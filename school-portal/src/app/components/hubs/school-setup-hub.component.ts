import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HubTabsComponent, HubTab } from '../hub-tabs/hub-tabs.component';

@Component({
  selector: 'app-school-setup-hub',
  standalone: true,
  imports: [RouterOutlet, HubTabsComponent],
  template: `
    <app-hub-tabs [tabs]="tabs" />
    <router-outlet />
  `
})
export class SchoolSetupHubComponent {
  tabs: HubTab[] = [
    { path: '/school-setup/classes', label: 'Classes & Sections', icon: '📚' },
    { path: '/school-setup/academic-years', label: 'Academic Years', icon: '📅' },
    { path: '/school-setup/subjects', label: 'Subjects', icon: '📖' },
    { path: '/school-setup/promote', label: 'Promote Students', icon: '🎓' },
  ];
}
