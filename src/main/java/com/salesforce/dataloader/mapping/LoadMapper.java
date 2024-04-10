/*
 * Copyright (c) 2015, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dataloader.mapping;

import com.salesforce.dataloader.client.PartnerClient;
import com.salesforce.dataloader.exception.MappingInitializationException;
import com.salesforce.dataloader.model.Row;
import com.salesforce.dataloader.util.AppUtil;
import com.sforce.soap.partner.Field;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Mapper which maps field names for loading operations. Field names are mapped from dao (local) name to sfdc name.
 * 
 * @author Colin Jarvis
 * @since 21.0
 */
public class LoadMapper extends Mapper {

    private static final Logger logger = LogManager.getLogger(Mapper.class);

    public LoadMapper(PartnerClient client, Collection<String> columnNames, Field[] fields, String mappingFileName)
            throws MappingInitializationException {
        super(client, columnNames, fields, mappingFileName);
    }

    @Override
    protected void putPropertyEntry(Entry<Object, Object> entry) {
        String dao = (String)entry.getKey();
        String sfdc = (String)entry.getValue();
        if (isConstant(dao))
            putConstant(sfdc, dao);
        else if (!hasDaoColumns() || hasDaoColumn(dao)) putMapping(dao, sfdc);
    }

    public Map<String, String> getMappingWithUnmappedColumns(boolean includeUnmapped) {
        final CaseInsensitiveMap result = new CaseInsensitiveMap();
        
        // get mappings in the same order as DAO column order
        for (String daoColumn : getDaoColumns()) {
            String mapping = getMapping(daoColumn);
            if (includeUnmapped || mapping != null) {
                result.put(daoColumn, mapping);
            }
        }
        
        // Make sure to not miss existing mappings even if they are not in DAO.
        for (Map.Entry<String, String> currentMapEntry : getMap().entrySet()) {
            if (!result.containsKey(currentMapEntry.getKey())) {
                result.put(currentMapEntry.getKey(), currentMapEntry.getValue());
            }
        }
        
        return result;
    }

    public Row mapData(Row localRow) {
        Row mappedData = new Row();
        for (Map.Entry<String, Object> entry : localRow.entrySet()) {
            String sfdcNameList = getMapping(entry.getKey(), true);
            if (StringUtils.hasText(sfdcNameList)) {
                String sfdcNameArray[] = sfdcNameList.split(AppUtil.COMMA);
                for (String sfdcName : sfdcNameArray) {
                    mappedData.put(sfdcName.trim(), entry.getValue());
                }
            } else {
                logger.info("Mapping for field " + entry.getKey() + " will be ignored since destination column is empty");
            }
        }
        mapConstants(mappedData);
        return mappedData;
    }

    public void verifyMappingsAreValid() throws MappingInitializationException {
        for (Map.Entry<String, String> entry : getMappingWithUnmappedColumns(false).entrySet()) {
            String sfdcNameList = entry.getValue();
            if(StringUtils.hasText(sfdcNameList)) {
                String sfdcNameArray[] = sfdcNameList.split(AppUtil.COMMA);
                for (String sfdcName : sfdcNameArray) {
                    final Field f = getClient().getField(sfdcName.trim());
                    if (f == null)
                        throw new MappingInitializationException("Field mapping is invalid: " + entry.getKey() + " => " + sfdcName);
                }
            }
        }
    }
    
    public List<String> getMappedDaoColumns() {
        Map<String, String> possibleMappings = this.getMappingWithUnmappedColumns(true);
        LinkedList<String> mappedColList = new LinkedList<String>();
        for (String daoCol : possibleMappings.keySet()) {
            String mappedName = this.map.get(daoCol);
            if (mappedName != null) {
                if (mappedName.contains(",")) {
                    String[] mappedNameList = mappedName.split(",");
                    for (int i=0; i<mappedNameList.length; i++) {
                        mappedColList.add(mappedNameList[i]);
                    }
                } else {
                    mappedColList.add(daoCol);
                }
            }
        }
        return mappedColList;
    }

}