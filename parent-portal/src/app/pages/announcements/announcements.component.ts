import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-announcements',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './announcements.component.html',
  styleUrl: './announcements.component.css'
})
export class AnnouncementsComponent implements OnInit {
  allAnnouncements: any[] = [];
  announcements = signal<any[]>([]);
  selectedAnnouncement = signal<any | null>(null);

  filterScope = '';
  filterCategory = '';

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.get<any[]>('/api/parent/announcements').subscribe(res => {
      this.allAnnouncements = res.data || [];
      this.applyFilter();
    });
  }

  applyFilter() {
    let list = this.allAnnouncements;
    if (this.filterScope) {
      list = list.filter(a => a.scopeType === this.filterScope);
    }
    if (this.filterCategory) {
      list = list.filter(a => a.category === this.filterCategory);
    }
    this.announcements.set(list);
  }

  openDetail(a: any) {
    this.selectedAnnouncement.set(a);
  }

  closeDetail() {
    this.selectedAnnouncement.set(null);
  }

  getBodyPreview(body: string): string {
    if (!body) return '';
    return body.length > 120 ? body.substring(0, 120) + '...' : body;
  }
}
