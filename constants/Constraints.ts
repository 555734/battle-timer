export type ConstraintType = 'normal' | 'hard' | 'fun';

export interface Constraint {
  id: number;
  text: string;
  type: ConstraintType;
}

export const CONSTRAINTS: Constraint[] = [
  // Normal
  { id: 1, text: '3文字ちょうど', type: 'normal' },
  { id: 2, text: '4文字ちょうど', type: 'normal' },
  { id: 3, text: '5文字ちょうど', type: 'normal' },
  { id: 4, text: '食べ物限定', type: 'normal' },
  { id: 5, text: '動物限定', type: 'normal' },
  { id: 6, text: '国名・地名限定', type: 'normal' },
  { id: 7, text: '「あ段」で終わる言葉', type: 'normal' },
  { id: 8, text: '「い段」で終わる言葉', type: 'normal' },
  
  // Hard
  { id: 101, text: '「あ行」使用禁止', type: 'hard' },
  { id: 102, text: '「か行」使用禁止', type: 'hard' },
  { id: 103, text: '「さ行」使用禁止', type: 'hard' },
  { id: 104, text: 'カタカナ語禁止（外来語NG）', type: 'hard' },
  { id: 105, text: '濁点・半濁点を含める', type: 'hard' },
  { id: 106, text: '2回以上同じ母音禁止', type: 'hard' },
  { id: 107, text: '前の人の最後の文字を含む', type: 'hard' },

  // Fun/Action
  { id: 201, text: '英語風の発音で', type: 'fun' },
  { id: 202, text: 'ゆっくり喋る', type: 'fun' },
  { id: 203, text: '早口で喋る', type: 'fun' },
  { id: 204, text: '語尾に「ごわす」をつける', type: 'fun' },
];
