import { useEffect, useRef, useState } from 'react';

export default function SearchField({
  resetKey,
  onText,
}: {
  resetKey: string;
  onText: (text: string) => Promise<void>;
}) {
  const [text, setText] = useState('');
  const composing = useRef(false);
  useEffect(() => {
    setText('');
    composing.current = false;
  }, [resetKey]);
  return (
    <>
      <div
        style={{
          display: 'flex',
          height: 46,
          background: 'white',
          alignItems: 'center',
          gap: 4,
          fontFamily: 'system-ui',
        }}
      >
        <input
          aria-label="検索する名前"
          placeholder="名前を検索"
          value={text}
          maxLength={200}
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="none"
          spellCheck={false}
          style={{
            boxSizing: 'border-box',
            width: '100%',
            height: 46,
            minWidth: 0,
            border: '1px solid #A5BDC9',
            borderRadius: 6,
            padding: '0 10px',
            fontSize: 17,
            color: '#162832',
          }}
          onCompositionStart={() => {
            composing.current = true;
          }}
          onCompositionEnd={(event) => {
            composing.current = false;
            void onText(event.currentTarget.value);
          }}
          onChange={(event) => {
            const value = event.currentTarget.value;
            setText(value);
            if (!composing.current) void onText(value);
          }}
        />
        {text && (
          <button
            aria-label="検索を解除"
            style={{
              border: 0,
              background: 'white',
              color: '#147FB5',
              fontSize: 22,
              minWidth: 44,
              height: 44,
            }}
            onClick={() => {
              setText('');
              composing.current = false;
              void onText('');
            }}
          >
            ×
          </button>
        )}
      </div>
    </>
  );
}
