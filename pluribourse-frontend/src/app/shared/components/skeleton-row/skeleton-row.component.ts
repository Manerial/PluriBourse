import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-skeleton-row',
  standalone: true,
  imports: [],
  templateUrl: './skeleton-row.component.html',
  styleUrl: './skeleton-row.component.scss',
})
export class SkeletonRowComponent {
  readonly rows = input<number>(3);
  readonly rowsArray = computed(() => Array(Math.max(0, this.rows())).fill(0));
}
