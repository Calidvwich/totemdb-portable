package edu.whu.tmdb.query.operations.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.whu.tmdb.query.operations.Exception.TMDBException;
import edu.whu.tmdb.query.operations.utils.MemConnect;
import edu.whu.tmdb.query.operations.utils.SelectResult;
import edu.whu.tmdb.storage.memory.MemManager;
import edu.whu.tmdb.storage.memory.Tuple;
import edu.whu.tmdb.storage.memory.TupleList;
import edu.whu.tmdb.storage.memory.SystemTable.BiPointerTableItem;
import edu.whu.tmdb.storage.memory.SystemTable.DeputyRuleTableItem;
import edu.whu.tmdb.storage.memory.SystemTable.DeputyTableItem;
import edu.whu.tmdb.storage.memory.SystemTable.ObjectTableItem;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

final class GroupDeputySynchronizer {
    private final MemConnect memConnect;

    GroupDeputySynchronizer() {
        memConnect = MemConnect.getInstance(MemManager.getInstance());
    }

    void synchronizeForSource(int sourceClassId) throws TMDBException, IOException {
        Set<Integer> deputyIds = new HashSet<>();
        for (DeputyTableItem relation : MemConnect.getDeputyTableList()) {
            if (relation.originid == sourceClassId
                    && "groupdeputy".equals(getRule(relation.ruleid, 1))) {
                deputyIds.add(relation.deputyid);
            }
        }
        for (int deputyId : deputyIds) {
            synchronize(sourceClassId, deputyId);
        }
    }

    private void synchronize(int sourceClassId, int deputyId) throws TMDBException, IOException {
        String sql = getDeputyRule(deputyId, 0);
        SelectResult result;
        try {
            Statement statement = CCJSqlParserUtil.parse(
                new ByteArrayInputStream(sql.getBytes())
            );
            result = new SelectImpl().select(statement);
        } catch (JSQLParserException e) {
            throw new IllegalStateException("Invalid GroupDeputy rule: " + sql, e);
        }

        Map<Object, Tuple> existingByGroup = new HashMap<>();
        for (Tuple tuple : memConnect.getTupleList(deputyId).tuplelist) {
            existingByGroup.put(tuple.tuple[0], tuple);
        }

        removePointersToDeputy(deputyId);
        Set<Object> desiredGroups = new HashSet<>();
        List<String> deputyColumns = memConnect.getColumns(memConnect.getClassName(deputyId));
        HashMap<Object, ArrayList<Integer>> groupMap = result.getGroupMap();
        TupleList desiredTuples = result.getTpl();

        for (Tuple desired : desiredTuples.tuplelist) {
            Object group = desired.tuple[0];
            desiredGroups.add(group);
            Tuple existing = existingByGroup.get(group);
            int deputyObjectId;
            if (existing == null) {
                deputyObjectId = new InsertImpl().executeOne(
                    deputyId,
                    deputyColumns,
                    new Tuple(desired.tuple.clone())
                );
            } else {
                existing.tuple = desired.tuple.clone();
                existing.tupleSize = desired.tuple.length;
                memConnect.UpateTuple(existing, existing.tupleId);
                deputyObjectId = existing.tupleId;
            }

            ArrayList<Integer> sourceObjectIds = groupMap.get(group);
            if (sourceObjectIds != null) {
                for (int sourceObjectId : sourceObjectIds) {
                    MemConnect.getBiPointerTableList().add(
                        new BiPointerTableItem(
                            sourceClassId,
                            sourceObjectId,
                            deputyId,
                            deputyObjectId
                        )
                    );
                }
            }
        }

        for (Map.Entry<Object, Tuple> entry : existingByGroup.entrySet()) {
            if (!desiredGroups.contains(entry.getKey())) {
                deleteDeputyTuple(entry.getValue());
            }
        }
    }

    private void removePointersToDeputy(int deputyId) {
        Iterator<BiPointerTableItem> iterator = MemConnect.getBiPointerTableList().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().deputyid == deputyId) {
                iterator.remove();
            }
        }
    }

    private void deleteDeputyTuple(Tuple tuple) {
        memConnect.DeleteTuple(tuple.tupleId);
        Iterator<ObjectTableItem> iterator = MemConnect.getObjectTableList().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().tupleid == tuple.tupleId) {
                iterator.remove();
            }
        }
    }

    private String getDeputyRule(int deputyId, int index) {
        for (DeputyTableItem relation : MemConnect.getDeputyTableList()) {
            if (relation.deputyid == deputyId) {
                return getRule(relation.ruleid, index);
            }
        }
        return "";
    }

    private String getRule(int ruleId, int index) {
        for (DeputyRuleTableItem rule : MemConnect.getDeputyRuleTableList()) {
            if (rule.ruleid == ruleId && rule.deputyrule.length > index) {
                return rule.deputyrule[index];
            }
        }
        return "";
    }
}
