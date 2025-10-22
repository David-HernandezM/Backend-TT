package com.ipn.escom.conversor_sql.conversion;

import java.util.stream.Collectors;

import com.ipn.escom.conversor_sql.ar.ArAttr;
import com.ipn.escom.conversor_sql.ar.ArCross;
import com.ipn.escom.conversor_sql.ar.ArIntersect;
import com.ipn.escom.conversor_sql.ar.ArJoin;
import com.ipn.escom.conversor_sql.ar.ArMinus;
import com.ipn.escom.conversor_sql.ar.ArNodeInterface;
import com.ipn.escom.conversor_sql.ar.ArProject;
import com.ipn.escom.conversor_sql.ar.ArRename;
import com.ipn.escom.conversor_sql.ar.ArSelect;
import com.ipn.escom.conversor_sql.ar.ArSemiJoin;
import com.ipn.escom.conversor_sql.ar.ArTable;
import com.ipn.escom.conversor_sql.ar.ArUnion;
import com.ipn.escom.conversor_sql.core.CoreCross;
import com.ipn.escom.conversor_sql.core.CoreIntersect;
import com.ipn.escom.conversor_sql.core.CoreJoin;
import com.ipn.escom.conversor_sql.core.CoreMinus;
import com.ipn.escom.conversor_sql.core.CoreNodeInterface;
import com.ipn.escom.conversor_sql.core.CoreProject;
import com.ipn.escom.conversor_sql.core.CoreRename;
import com.ipn.escom.conversor_sql.core.CoreSelect;
import com.ipn.escom.conversor_sql.core.CoreSemiJoin;
import com.ipn.escom.conversor_sql.core.CoreTable;
import com.ipn.escom.conversor_sql.core.CoreUnion;

public class CoreToAr {
  public ArNodeInterface convert(CoreNodeInterface n) {
    return switch (n) {
      case CoreTable t      -> new ArTable(t.alias() != null ? t.alias() : t.name());
      case CoreRename r     -> new ArRename(convert(r.input()), r.newName());
      case CoreProject p    -> new ArProject(convert(p.input()),
          p.attrs().stream().map(a -> new ArAttr(a.rel(), a.name())).collect(Collectors.toList()));
      case CoreSelect s     -> new ArSelect(convert(s.input()), PredMapper.fake()); // MVP: pred fake
      case CoreJoin j       -> new ArJoin(convert(j.left()), convert(j.right()), PredMapper.fake());
      case CoreCross c      -> new ArCross(convert(c.left()), convert(c.right()));
      case CoreUnion u      -> new ArUnion(convert(u.left()), convert(u.right()));
      case CoreIntersect i  -> new ArIntersect(convert(i.left()), convert(i.right()));
      case CoreMinus m      -> new ArMinus(convert(m.left()), convert(m.right()));
      case CoreSemiJoin sj  -> new ArSemiJoin(convert(sj.left()), convert(sj.right()), PredMapper.fake());
	default -> throw new IllegalArgumentException("Unexpected value: " + n);
    };
  }
}