import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-support',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './support.component.html',
  styleUrl: './support.component.css'
})
export class SupportComponent implements OnInit {
  tickets = signal<any[]>([]);
  selectedTicket = signal<any>(null);
  newComment = '';
  filterStatus = '';

  constructor(private api: ApiService) {}

  ngOnInit() { this.loadTickets(); }

  loadTickets() {
    this.api.get<any[]>('/api/platform/support-tickets').subscribe(res => this.tickets.set(res.data || []));
  }

  get filteredTickets(): any[] {
    if (!this.filterStatus) return this.tickets();
    return this.tickets().filter(t => t.status === this.filterStatus);
  }

  get openCount(): number { return this.tickets().filter(t => t.status === 'OPEN').length; }
  get inProgressCount(): number { return this.tickets().filter(t => t.status === 'IN_PROGRESS').length; }

  openTicket(ticket: any) {
    this.api.get<any>(`/api/platform/support-tickets/${ticket.id}`).subscribe(res => this.selectedTicket.set(res.data));
  }

  closeDetail() { this.selectedTicket.set(null); }

  addComment() {
    if (!this.newComment.trim()) return;
    const tid = this.selectedTicket()?.id;
    this.api.post(`/api/platform/support-tickets/${tid}/comments`, { body: this.newComment }).subscribe({
      next: () => { this.newComment = ''; this.openTicket({ id: tid }); this.loadTickets(); },
      error: () => {}
    });
  }

  updateStatus(status: string) {
    const tid = this.selectedTicket()?.id;
    this.api.patch(`/api/platform/support-tickets/${tid}/status`, { status }).subscribe({
      next: () => { this.openTicket({ id: tid }); this.loadTickets(); },
      error: () => {}
    });
  }

  getPriorityColor(p: string): string {
    if (p === 'URGENT') return '#b0473f';
    if (p === 'HIGH') return '#ba7517';
    if (p === 'LOW') return '#888';
    return '#4a6b8a';
  }

  getStatusColor(s: string): string {
    if (s === 'OPEN') return '#ba7517';
    if (s === 'IN_PROGRESS') return '#4a6b8a';
    if (s === 'RESOLVED') return '#2d6a4f';
    return '#888';
  }
}
