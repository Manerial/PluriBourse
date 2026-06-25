import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [],
  templateUrl: './empty-state.component.html',
  styleUrl: './empty-state.component.scss',
})
export class EmptyStateComponent {
  readonly icon = input.required<string>();
  readonly message = input.required<string>();
  readonly actionLabel = input<string | undefined>(undefined);
  readonly action = output<void>();
}
