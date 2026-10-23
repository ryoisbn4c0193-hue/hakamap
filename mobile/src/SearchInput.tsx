'use dom';

import type { DOMProps } from 'expo/dom';
import SearchField from './SearchField';

export default function SearchInput(props: {
  resetKey: string;
  onText: (text: string) => Promise<void>;
  dom?: DOMProps;
}) {
  return (
    <>
      <style>
        {'html,body,#root{margin:0;padding:0;width:100%;overflow:hidden}*{box-sizing:border-box}'}
      </style>
      <SearchField {...props} />
    </>
  );
}
