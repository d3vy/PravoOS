package com.pravoos.ai.recyclebin.api;

import java.util.List;

public record BinContents(BinSnapshot root, List<BinSnapshot> cascaded) {

  public BinContents {
    cascaded = cascaded == null ? List.of() : List.copyOf(cascaded);
  }

  public static BinContents of(BinSnapshot root) {
    return new BinContents(root, List.of());
  }
}
